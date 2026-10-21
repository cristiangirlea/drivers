#ifdef _WIN32

#include <Windows.h>
#include <odbcinst.h>
#include <sql.h>

#include <cstdlib>
#include <cwchar>
#include <memory>
#include <optional>
#include <string>
#include <system_error>
#include <utility>
#include <vector>

#include <catch2/catch_test_macros.hpp>

#include "ODBCConfig.hpp"

using ConfigDriverFn = int(__stdcall*)(HWND, WORD, LPCSTR, LPCSTR, LPSTR, WORD, WORD*);
using ConfigDSNWFn = int(__stdcall*)(HWND, WORD, LPCWSTR, LPCWSTR);
using ConfigDSNFn = int(__stdcall*)(HWND, WORD, LPCSTR, LPCSTR);

static const std::wstring ODBC_INI_REG_KEY = L"SOFTWARE\\ODBC\\ODBC.INI";

static std::string get_driver_path() {
  const char* path = std::getenv("DRIVER_PATH");
  if (path == nullptr || path[0] == '\0') {
    FAIL("DRIVER_PATH environment variable not set");
  }
  return path;
}

static std::wstring widen(const std::string& s) {
  if (s.empty()) return {};
  const int len = MultiByteToWideChar(CP_ACP, 0, s.data(), static_cast<int>(s.size()), nullptr, 0);
  REQUIRE(len > 0);
  std::wstring out(static_cast<size_t>(len), L'\0');
  REQUIRE(MultiByteToWideChar(CP_ACP, 0, s.data(), static_cast<int>(s.size()), out.data(), len) == len);
  return out;
}

static std::string narrow(const std::wstring& s) {
  if (s.empty()) return {};
  const int len = WideCharToMultiByte(CP_ACP, 0, s.data(), static_cast<int>(s.size()), nullptr, 0, nullptr, nullptr);
  REQUIRE(len > 0);
  std::string out(static_cast<size_t>(len), '\0');
  REQUIRE(WideCharToMultiByte(CP_ACP, 0, s.data(), static_cast<int>(s.size()), out.data(), len, nullptr, nullptr) ==
          len);
  return out;
}

static std::vector<wchar_t> build_attrs_w(const std::vector<std::pair<std::string, std::string>>& pairs) {
  std::vector<wchar_t> buf;
  for (const auto& [k, v] : pairs) {
    const std::wstring entry = widen(k) + L'=' + widen(v);
    buf.insert(buf.end(), entry.begin(), entry.end());
    buf.push_back(L'\0');
  }
  buf.push_back(L'\0');
  return buf;
}

static std::vector<char> build_attrs_a(const std::vector<std::pair<std::string, std::string>>& pairs) {
  std::vector<char> buf;
  for (const auto& [k, v] : pairs) {
    const std::string entry = k + '=' + v;
    buf.insert(buf.end(), entry.begin(), entry.end());
    buf.push_back('\0');
  }
  buf.push_back('\0');
  return buf;
}

class DynLib {
  HMODULE handle_ = nullptr;

 public:
  DynLib(const char* name) {
    handle_ = LoadLibraryA(name);
    REQUIRE(handle_ != nullptr);
  }

  ~DynLib() {
    if (handle_) FreeLibrary(handle_);
  }

  DynLib(const DynLib&) = delete;
  DynLib& operator=(const DynLib&) = delete;

  template <typename T>
  T get(const char* name) {
    auto fn = reinterpret_cast<T>(GetProcAddress(handle_, name));
    REQUIRE(fn != nullptr);
    return fn;
  }
};

class DriverDll : public DynLib {
 public:
  DriverDll() : DynLib(get_driver_path().c_str()) {}
};

// SQLWriteDSNToIni resolves the driver from ODBCINST.INI, so the save path needs a registered driver.
static WindowsConfigInstallation install_registered_driver(const std::shared_ptr<DriverConfig>& driver) {
  try {
    return WindowsConfigInstallation::register_driver(driver);
  } catch (const std::system_error& e) {
    if (e.code().value() == ERROR_ACCESS_DENIED) {
      SKIP("Registering a test ODBC driver under HKLM requires administrator rights");
    }
    throw;
  }
}

static std::string unique_dsn_name() {
  return "SfConfigDsnTest_" + std::to_string(GetCurrentProcessId()) + "_" + std::to_string(GetTickCount64());
}

// Sets the process-wide installer config mode to ODBC_USER_DSN so ConfigDSN writes to HKCU.
// With no values this writes nothing to the registry; the returned installation removes the
// DSN that ConfigDSN creates.
static WindowsConfigInstallation install_user_dsn(const std::string& name,
                                                  const std::vector<std::pair<std::string, std::string>>& values = {}) {
  REQUIRE(SQLSetConfigMode(ODBC_USER_DSN));
  DataSourceConfig dsn;
  dsn.name(name);
  for (const auto& [k, v] : values) {
    dsn.set(k, v);
  }
  return dsn.install();
}

static bool user_dsn_exists(const std::string& dsn) {
  HKEY hkey = nullptr;
  const std::wstring key = ODBC_INI_REG_KEY + L"\\" + widen(dsn);
  if (RegOpenKeyExW(HKEY_CURRENT_USER, key.c_str(), 0, KEY_READ, &hkey) != ERROR_SUCCESS) return false;
  RegCloseKey(hkey);
  return true;
}

static std::optional<std::string> read_user_dsn_value(const std::string& dsn, const std::string& value_name) {
  const std::wstring key = ODBC_INI_REG_KEY + L"\\" + widen(dsn);
  const std::wstring name = widen(value_name);
  DWORD size = 0;
  LONG res = RegGetValueW(HKEY_CURRENT_USER, key.c_str(), name.c_str(), RRF_RT_REG_SZ, nullptr, nullptr, &size);
  if (res == ERROR_FILE_NOT_FOUND) return std::nullopt;
  REQUIRE(res == ERROR_SUCCESS);
  std::wstring buf(size / sizeof(wchar_t), L'\0');
  res = RegGetValueW(HKEY_CURRENT_USER, key.c_str(), name.c_str(), RRF_RT_REG_SZ, nullptr, buf.data(), &size);
  REQUIRE(res == ERROR_SUCCESS);
  buf.resize(wcsnlen(buf.c_str(), buf.size()));
  return narrow(buf);
}

// ============================================================================
// ConfigDriver tests
// ============================================================================

TEST_CASE("ConfigDriver returns TRUE", "[odbc-api][setup-dll][config-driver]") {
  DriverDll dll;
  auto fn = dll.get<ConfigDriverFn>("ConfigDriver");
  WORD cbOut = 0xFFFF;
  int ret = fn(nullptr, ODBC_ADD_DSN, "TestDriver", nullptr, nullptr, 0, &cbOut);
  REQUIRE(ret == 1);
  REQUIRE(cbOut == 0);
}

TEST_CASE("ConfigDriver with NULL pcbMsgOut", "[odbc-api][setup-dll][config-driver]") {
  DriverDll dll;
  auto fn = dll.get<ConfigDriverFn>("ConfigDriver");
  int ret = fn(nullptr, ODBC_REMOVE_DSN, "TestDriver", nullptr, nullptr, 0, nullptr);
  REQUIRE(ret == 1);
}

// ============================================================================
// ConfigDSNW error-handling tests (no registry interaction)
// ============================================================================

TEST_CASE("ConfigDSNW: returns FALSE with missing DSN attribute", "[odbc-api][setup-dll][config-dsn]") {
  DriverDll dll;
  auto config_dsn_w = dll.get<ConfigDSNWFn>("ConfigDSNW");
  std::wstring driver = L"Snowflake ODBC";

  auto attrs = build_attrs_w({{"SERVER", "test.snowflake.com"}});
  int ret = config_dsn_w(nullptr, ODBC_ADD_DSN, driver.c_str(), attrs.data());
  REQUIRE(ret == 0);
}

TEST_CASE("ConfigDSNW: returns FALSE with NULL attributes", "[odbc-api][setup-dll][config-dsn]") {
  DriverDll dll;
  auto config_dsn_w = dll.get<ConfigDSNWFn>("ConfigDSNW");
  std::wstring driver = L"Snowflake ODBC";

  int ret = config_dsn_w(nullptr, ODBC_ADD_DSN, driver.c_str(), nullptr);
  REQUIRE(ret == 0);
}

TEST_CASE("ConfigDSNW: returns FALSE for invalid request code", "[odbc-api][setup-dll][config-dsn]") {
  DriverDll dll;
  auto config_dsn_w = dll.get<ConfigDSNWFn>("ConfigDSNW");
  std::wstring driver = L"Snowflake ODBC";

  auto attrs = build_attrs_w({{"DSN", "SomeDSN"}});
  int ret = config_dsn_w(nullptr, 99, driver.c_str(), attrs.data());
  REQUIRE(ret == 0);
}

// ============================================================================
// ConfigDSN (ANSI) error-handling tests
// ============================================================================

TEST_CASE("ConfigDSN (ANSI): returns FALSE with NULL attributes", "[odbc-api][setup-dll][config-dsn]") {
  DriverDll dll;
  auto config_dsn = dll.get<ConfigDSNFn>("ConfigDSN");

  int ret = config_dsn(nullptr, ODBC_ADD_DSN, "Snowflake ODBC", nullptr);
  REQUIRE(ret == 0);
}

// ============================================================================
// ConfigDSNW / ConfigDSN save tests (registry interaction, no window)
// ============================================================================

TEST_CASE("ConfigDSNW: ODBC_ADD_DSN without a window returns TRUE and persists attributes",
          "[odbc-api][setup-dll][config-dsn]") {
  DriverDll dll;
  auto config_dsn_w = dll.get<ConfigDSNWFn>("ConfigDSNW");
  const auto driver = DriverConfig::Default();
  const auto driver_installation = install_registered_driver(driver);
  const std::string dsn = unique_dsn_name();
  const auto dsn_installation = install_user_dsn(dsn);

  auto attrs = build_attrs_w({{"DSN", dsn}, {"SERVER", "snowflake.com"}});
  int ret = config_dsn_w(nullptr, ODBC_ADD_DSN, widen(driver->name()).c_str(), attrs.data());
  REQUIRE(ret == 1);

  const auto server = read_user_dsn_value(dsn, "SERVER");
  REQUIRE(server.has_value());
  CHECK(*server == "snowflake.com");
  CHECK_FALSE(read_user_dsn_value(dsn, "TRACING").has_value());
}

TEST_CASE("ConfigDSNW: ODBC_CONFIG_DSN on an existing DSN returns TRUE", "[odbc-api][setup-dll][config-dsn]") {
  DriverDll dll;
  auto config_dsn_w = dll.get<ConfigDSNWFn>("ConfigDSNW");
  const auto driver = DriverConfig::Default();
  const auto driver_installation = install_registered_driver(driver);
  const std::string dsn = unique_dsn_name();
  const auto dsn_installation = install_user_dsn(dsn, {{"SERVER", "old.snowflake.com"}});

  auto attrs = build_attrs_w({{"DSN", dsn}, {"SERVER", "snowflake.com"}});
  int ret = config_dsn_w(nullptr, ODBC_CONFIG_DSN, widen(driver->name()).c_str(), attrs.data());
  REQUIRE(ret == 1);

  const auto server = read_user_dsn_value(dsn, "SERVER");
  REQUIRE(server.has_value());
  CHECK(*server == "snowflake.com");
}

TEST_CASE("ConfigDSNW: ODBC_CONFIG_DSN on a DSN with a legacy TRACING value returns TRUE and keeps it",
          "[odbc-api][setup-dll][config-dsn]") {
  DriverDll dll;
  auto config_dsn_w = dll.get<ConfigDSNWFn>("ConfigDSNW");
  const auto driver = DriverConfig::Default();
  const auto driver_installation = install_registered_driver(driver);
  const std::string dsn = unique_dsn_name();
  const auto dsn_installation = install_user_dsn(dsn, {{"SERVER", "snowflake.com"}, {"TRACING", "4"}});

  auto attrs = build_attrs_w({{"DSN", dsn}, {"SERVER", "snowflake.com"}});
  int ret = config_dsn_w(nullptr, ODBC_CONFIG_DSN, widen(driver->name()).c_str(), attrs.data());
  REQUIRE(ret == 1);

  const auto tracing = read_user_dsn_value(dsn, "TRACING");
  REQUIRE(tracing.has_value());
  CHECK(*tracing == "4");
  const auto server = read_user_dsn_value(dsn, "SERVER");
  REQUIRE(server.has_value());
  CHECK(*server == "snowflake.com");
}

TEST_CASE("ConfigDSN (ANSI): ODBC_ADD_DSN without a window returns TRUE", "[odbc-api][setup-dll][config-dsn]") {
  DriverDll dll;
  auto config_dsn = dll.get<ConfigDSNFn>("ConfigDSN");
  const auto driver = DriverConfig::Default();
  const auto driver_installation = install_registered_driver(driver);
  const std::string dsn = unique_dsn_name();
  const auto dsn_installation = install_user_dsn(dsn);

  auto attrs = build_attrs_a({{"DSN", dsn}, {"SERVER", "snowflake.com"}});
  int ret = config_dsn(nullptr, ODBC_ADD_DSN, driver->name().c_str(), attrs.data());
  REQUIRE(ret == 1);

  const auto server = read_user_dsn_value(dsn, "SERVER");
  REQUIRE(server.has_value());
  CHECK(*server == "snowflake.com");
}

TEST_CASE("ConfigDSNW: ODBC_REMOVE_DSN removes a saved DSN", "[odbc-api][setup-dll][config-dsn]") {
  DriverDll dll;
  auto config_dsn_w = dll.get<ConfigDSNWFn>("ConfigDSNW");
  const auto driver = DriverConfig::Default();
  const auto driver_installation = install_registered_driver(driver);
  const std::string dsn = unique_dsn_name();
  const auto dsn_installation = install_user_dsn(dsn);
  const std::wstring driver_name = widen(driver->name());

  auto add_attrs = build_attrs_w({{"DSN", dsn}, {"SERVER", "snowflake.com"}});
  int ret = config_dsn_w(nullptr, ODBC_ADD_DSN, driver_name.c_str(), add_attrs.data());
  REQUIRE(ret == 1);
  REQUIRE(user_dsn_exists(dsn));

  auto remove_attrs = build_attrs_w({{"DSN", dsn}});
  ret = config_dsn_w(nullptr, ODBC_REMOVE_DSN, driver_name.c_str(), remove_attrs.data());
  REQUIRE(ret == 1);

  CHECK_FALSE(user_dsn_exists(dsn));
}

#endif  // _WIN32
