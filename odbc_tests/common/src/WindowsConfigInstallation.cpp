#ifdef _WIN32

#include <stdexcept>
#include <string>
#include <system_error>

#include "ODBCConfig.hpp"

static const std::string ODBC_INI_REG_KEY = "SOFTWARE\\ODBC\\ODBC.INI";
static const std::string ODBCINST_INI_REG_KEY = "SOFTWARE\\ODBC\\ODBCINST.INI";

static void reg_set_sz(HKEY root, const std::string& subkey, const std::string& name, const std::string& value) {
  HKEY hkey;
  LONG res =
      RegCreateKeyExA(root, subkey.c_str(), 0, nullptr, REG_OPTION_NON_VOLATILE, KEY_WRITE, nullptr, &hkey, nullptr);
  if (res != ERROR_SUCCESS) {
    std::string msg = "Failed to create registry key '" + subkey + "'";
    if (res == ERROR_ACCESS_DENIED && root == HKEY_LOCAL_MACHINE) {
      msg += " (ODBC driver registration on Windows requires administrator privileges)";
    }
    throw std::system_error(static_cast<int>(res), std::system_category(), msg);
  }
  res = RegSetValueExA(hkey, name.c_str(), 0, REG_SZ, reinterpret_cast<const BYTE*>(value.c_str()),
                       static_cast<DWORD>(value.size() + 1));
  RegCloseKey(hkey);
  if (res != ERROR_SUCCESS) {
    throw std::system_error(static_cast<int>(res), std::system_category(),
                            "Failed to set registry value '" + name + "' in key '" + subkey + "'");
  }
}

// Deletes are best-effort. Cleanup also visits entries that were never written, which fail with
// ERROR_FILE_NOT_FOUND, or with ERROR_ACCESS_DENIED under HKLM after a refused registration.
static void reg_delete_key_and_value(HKEY root, const std::string& base_key, const std::string& list_key,
                                     const std::string& name) {
  (void)RegDeleteKeyA(root, (base_key + "\\" + name).c_str());
  HKEY hkey;
  if (RegOpenKeyExA(root, (base_key + "\\" + list_key).c_str(), 0, KEY_SET_VALUE, &hkey) == ERROR_SUCCESS) {
    (void)RegDeleteValueA(hkey, name.c_str());
    RegCloseKey(hkey);
  }
}

// ============================================================================
// WindowsConfigInstallation
// ============================================================================

WindowsConfigInstallation WindowsConfigInstallation::install(const std::vector<DataSourceConfig>& data_sources) {
  return WindowsConfigInstallation(data_sources, {}, DriverRegistration::UserDsn);
}

WindowsConfigInstallation WindowsConfigInstallation::install_driver(
    const std::shared_ptr<DriverConfig>& driver_config) {
  return WindowsConfigInstallation({}, {driver_config}, DriverRegistration::UserDsn);
}

WindowsConfigInstallation WindowsConfigInstallation::register_driver(
    const std::shared_ptr<DriverConfig>& driver_config) {
  return WindowsConfigInstallation({}, {driver_config}, DriverRegistration::Odbcinst);
}

WindowsConfigInstallation::WindowsConfigInstallation(const std::vector<DataSourceConfig>& data_sources,
                                                     const std::set<std::shared_ptr<DriverConfig>>& driver_configs,
                                                     DriverRegistration driver_registration)
    : BaseConfigInstallation(data_sources, driver_configs), driver_registration_(driver_registration) {
  try {
    if (driver_registration_ == DriverRegistration::Odbcinst) {
      // The Microsoft DM and installer read ODBCINST.INI only from HKLM.
      for (const auto& dc : driver_configs_) {
        const std::string driver_key = ODBCINST_INI_REG_KEY + "\\" + dc->name();
        for (const auto& [k, v] : dc->parameters()) {
          reg_set_sz(HKEY_LOCAL_MACHINE, driver_key, k, v);
        }
        reg_set_sz(HKEY_LOCAL_MACHINE, ODBCINST_INI_REG_KEY + "\\ODBC Drivers", dc->name(), "Installed");
      }
    } else {
      // Registering in ODBCINST.INI requires admin. Instead, this creates a User DSN in HKCU that
      // references the driver DLL path directly — the DM loads the DLL from the DSN's "Driver" value.
      // Tests must use DSN= instead of DRIVER={} in their connection strings on Windows.
      for (const auto& dc : driver_configs_) {
        const std::string name = dc->name();
        reg_set_sz(HKEY_CURRENT_USER, ODBC_INI_REG_KEY + "\\ODBC Data Sources", name, name);
        const std::string dsn_key = ODBC_INI_REG_KEY + "\\" + name;
        auto it = dc->parameters().find("Driver");
        if (it != dc->parameters().end()) {
          reg_set_sz(HKEY_CURRENT_USER, dsn_key, "Driver", it->second);
        }
      }
    }
    for (const auto& ds : data_sources_) {
      const std::string dsn_name = ds.name();
      if (auto dc = ds.driver_config()) {
        reg_set_sz(HKEY_CURRENT_USER, ODBC_INI_REG_KEY + "\\ODBC Data Sources", dsn_name, dc.value()->name());
      }
      const std::string dsn_key = ODBC_INI_REG_KEY + "\\" + dsn_name;
      for (const auto& [k, v] : ds.parameters()) {
        if (!v.empty()) {
          reg_set_sz(HKEY_CURRENT_USER, dsn_key, k, v);
        }
      }
    }
  } catch (...) {
    cleanup();
    throw;
  }
}

void WindowsConfigInstallation::cleanup() noexcept {
  try {
    for (const auto& ds : data_sources_) {
      reg_delete_key_and_value(HKEY_CURRENT_USER, ODBC_INI_REG_KEY, "ODBC Data Sources", ds.name());
    }
    for (const auto& dc : driver_configs_) {
      if (driver_registration_ == DriverRegistration::Odbcinst) {
        reg_delete_key_and_value(HKEY_LOCAL_MACHINE, ODBCINST_INI_REG_KEY, "ODBC Drivers", dc->name());
      } else {
        reg_delete_key_and_value(HKEY_CURRENT_USER, ODBC_INI_REG_KEY, "ODBC Data Sources", dc->name());
      }
    }
  } catch (...) {
  }
}

WindowsConfigInstallation::~WindowsConfigInstallation() { cleanup(); }

WindowsConfigInstallation::WindowsConfigInstallation(WindowsConfigInstallation&& other) noexcept
    : BaseConfigInstallation(std::move(other)), driver_registration_(other.driver_registration_) {}

WindowsConfigInstallation& WindowsConfigInstallation::operator=(WindowsConfigInstallation&& other) noexcept {
  if (this != &other) {
    cleanup();
    BaseConfigInstallation::operator=(std::move(other));
    driver_registration_ = other.driver_registration_;
  }
  return *this;
}

#endif  // _WIN32
