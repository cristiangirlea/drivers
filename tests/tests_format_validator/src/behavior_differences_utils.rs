use anyhow::{Context, Result};
use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::Path;

pub const BD_ID_PATTERN: &str = r"BD#(?:\d+|[a-z][a-z0-9]+(?:-[a-z0-9]+)*)";

pub fn compile_bd_regex(pattern: &str) -> Regex {
    Regex::new(&pattern.replace("{bd}", BD_ID_PATTERN)).unwrap()
}

#[derive(Debug, Deserialize, Serialize)]
struct BehaviorDifference {
    name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    description: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    r#type: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
struct BehaviorDifferences {
    behavior_differences: HashMap<String, BehaviorDifference>,
}

/// Parse Behavior Differences descriptions from YAML content.
///
/// Parses YAML format with behavior_differences root key and extracts
/// name, description, and type information for each numbered entry.
pub fn parse_behavior_differences_descriptions(
    content: &str,
) -> Result<HashMap<String, String>, anyhow::Error> {
    let mut descriptions = HashMap::new();

    // Try to parse as YAML first
    if let Ok(yaml_data) = serde_yaml::from_str::<BehaviorDifferences>(content) {
        for (key, bd) in yaml_data.behavior_differences {
            let breaking_change_id = format!("BD#{key}");
            let full_description =
                format_bd_description(&bd.name, bd.r#type.as_deref(), bd.description.as_deref());
            descriptions.insert(breaking_change_id, full_description);
        }
        return Ok(descriptions);
    }

    // Fallback to markdown parsing for backward compatibility
    let lines: Vec<&str> = content.lines().collect();
    let mut i = 0;

    while i < lines.len() {
        let line = lines[i].trim();

        // Look for markdown headers like "## 1: Description"
        if line.starts_with("## ") && line.contains(": ") {
            if let Some(colon_pos) = line.find(": ") {
                let number_part = line[3..colon_pos].trim(); // Skip "## " and get number
                let title_part = line[colon_pos + 2..].trim(); // Get title after ": "

                if !number_part.is_empty() && !title_part.is_empty() {
                    let breaking_change_id = format!("BD#{number_part}");

                    // Collect the title and any additional details that follow
                    let mut full_description = title_part.to_string();

                    // Look ahead for additional detail lines
                    let mut j = i + 1;
                    while j < lines.len() {
                        let detail_line = lines[j].trim();

                        // Stop if we hit another header
                        if detail_line.starts_with("## ") || detail_line.starts_with("# ") {
                            break;
                        }

                        // Skip completely empty lines
                        if detail_line.is_empty() {
                            j += 1;
                            continue;
                        }

                        // Add detail line with proper spacing
                        if !full_description.is_empty() {
                            full_description.push_str("\n");
                        }
                        full_description.push_str(detail_line);

                        j += 1;
                    }

                    descriptions.insert(breaking_change_id, full_description);
                    i = j - 1; // Skip processed lines
                }
            }
        }
        i += 1;
    }

    Ok(descriptions)
}

fn format_bd_description(name: &str, bd_type: Option<&str>, description: Option<&str>) -> String {
    let mut full_description = match bd_type {
        Some(bd_type) => format!("[{bd_type}] {name}"),
        None => format!("[Behavior Difference] {name}"),
    };
    if let Some(additional_desc) = description {
        full_description.push_str(&format!("\n{additional_desc}"));
    }
    full_description
}

#[derive(Debug, Deserialize)]
struct BehaviorDifferenceFile {
    name: String,
    #[serde(default)]
    description: Option<String>,
    #[serde(default, rename = "type")]
    bd_type: Option<String>,
    #[serde(default)]
    legacy_id: Option<i64>,
}

pub fn load_behavior_difference_files(dir: &Path) -> Result<HashMap<String, String>> {
    let mut descriptions = HashMap::new();
    if !dir.is_dir() {
        return Ok(descriptions);
    }

    for area_entry in
        fs::read_dir(dir).with_context(|| format!("Failed to read {}", dir.display()))?
    {
        let area_entry = area_entry?;
        if !area_entry.file_type()?.is_dir() {
            continue;
        }
        for file_entry in fs::read_dir(area_entry.path())? {
            let file_entry = file_entry?;
            let path = file_entry.path();
            if !file_entry.file_type()?.is_file() {
                continue;
            }
            if path.extension().and_then(|ext| ext.to_str()) != Some("yaml") {
                continue;
            }
            let Some(slug) = path.file_stem().and_then(|stem| stem.to_str()) else {
                continue;
            };
            let content = fs::read_to_string(&path)
                .with_context(|| format!("Failed to read {}", path.display()))?;
            let parsed: BehaviorDifferenceFile = serde_yaml::from_str(&content)
                .with_context(|| format!("Failed to parse {}", path.display()))?;
            let description = format_bd_description(
                &parsed.name,
                parsed.bd_type.as_deref(),
                parsed.description.as_deref(),
            );
            match parsed.legacy_id.filter(|id| *id > 0) {
                Some(legacy_id) => {
                    descriptions
                        .entry(format!("BD#{slug}"))
                        .or_insert_with(|| description.clone());
                    descriptions
                        .entry(format!("BD#{legacy_id}"))
                        .or_insert(description);
                }
                None => {
                    descriptions
                        .entry(format!("BD#{slug}"))
                        .or_insert(description);
                }
            }
        }
    }

    Ok(descriptions)
}

#[cfg(test)]
mod tests {
    use super::*;
    use regex::Regex;
    use std::fs;

    #[test]
    fn test_parse_simple_behavior_differences() {
        let content = r#"# Behavior Differences

## 1: Simple Behavior Difference title

## 2: Another Behavior Difference title
"#;

        let result = parse_behavior_differences_descriptions(content).unwrap();
        assert_eq!(result.len(), 2);
        assert_eq!(
            result.get("BD#1"),
            Some(&"Simple Behavior Difference title".to_string())
        );
        assert_eq!(
            result.get("BD#2"),
            Some(&"Another Behavior Difference title".to_string())
        );
    }

    #[test]
    fn test_parse_behavior_differences_with_details() {
        let content = r#"# Behavior Differences

## 1: Behavior Difference with details
Some additional details here
More details on another line

## 2: Simple Behavior Difference
"#;

        let result = parse_behavior_differences_descriptions(content).unwrap();
        assert_eq!(result.len(), 2);
        assert_eq!(
            result.get("BD#1"),
            Some(
                &"Behavior Difference with details\nSome additional details here\nMore details on another line"
                    .to_string()
            )
        );
        assert_eq!(
            result.get("BD#2"),
            Some(&"Simple Behavior Difference".to_string())
        );
    }

    #[test]
    fn test_parse_behavior_differences_with_empty_lines() {
        let content = r#"# Behavior Differences

## 1: Behavior Difference title
Some details

More details after empty line

## 2: Another Behavior Difference
"#;

        let result = parse_behavior_differences_descriptions(content).unwrap();
        assert_eq!(result.len(), 2);
        assert_eq!(
            result.get("BD#1"),
            Some(
                &"Behavior Difference title\nSome details\nMore details after empty line"
                    .to_string()
            )
        );
    }

    #[test]
    fn test_parse_yaml_behaviour_differences() {
        let content = r#"behavior_differences:
  1:
    name: "Header of a gzip-compressed file does not contain filename"
  
  2:
    name: "DEFLATE compression type option is now correctly auto-detected"
  
  3:
    name: "BROTLI compression type option is now supported"
  
  4:
    name: "Error structure changed"
  
  5:
    name: "Private key password parameter name changed"
    description: |
      NEW driver: `private_key_password`
      OLD driver: `private_key_file_pwd`
"#;

        let result = parse_behavior_differences_descriptions(content).unwrap();
        assert_eq!(result.len(), 5);
        assert_eq!(
            result.get("BD#1"),
            Some(
                &"[Behavior Difference] Header of a gzip-compressed file does not contain filename"
                    .to_string()
            )
        );
        assert_eq!(
            result.get("BD#5"),
            Some(&"[Behavior Difference] Private key password parameter name changed\nNEW driver: `private_key_password`\nOLD driver: `private_key_file_pwd`\n".to_string())
        );
    }

    #[test]
    fn test_parse_yaml_with_types() {
        let content = r#"behavior_differences:
  1:
    name: "New feature implementation"
    type: "New Feature"
  
  2:
    name: "Bug fix for authentication"
    type: "Bug Fix"
  
  3:
    name: "Breaking change in API"
    type: "Breaking Change"
"#;

        let result = parse_behavior_differences_descriptions(content).unwrap();
        assert_eq!(result.len(), 3);
        assert_eq!(
            result.get("BD#1"),
            Some(&"[New Feature] New feature implementation".to_string())
        );
        assert_eq!(
            result.get("BD#2"),
            Some(&"[Bug Fix] Bug fix for authentication".to_string())
        );
        assert_eq!(
            result.get("BD#3"),
            Some(&"[Breaking Change] Breaking change in API".to_string())
        );
    }

    #[test]
    fn bd_id_pattern_matches_number_and_slug() {
        let re = Regex::new(BD_ID_PATTERN).unwrap();
        assert_eq!(re.find("BD#42").unwrap().as_str(), "BD#42");
        assert_eq!(
            re.find("OLD_DRIVER_ONLY(\"BD#private-key-password-renamed\")")
                .unwrap()
                .as_str(),
            "BD#private-key-password-renamed"
        );
        assert!(re.find("BD#").is_none());
        assert_eq!(re.find("BD#ab-c").unwrap().as_str(), "BD#ab-c");
        assert!(re.find("BD#a-b").is_none());
    }

    #[test]
    fn load_behavior_difference_file_slug_and_legacy_id() {
        let dir = tempfile::tempdir().unwrap();
        let area = dir.path().join("auth");
        fs::create_dir(&area).unwrap();
        fs::write(
            area.join("private-key-password-renamed.yaml"),
            r#"name: "Private key password parameter name changed"
type: api_incompatibility
legacy_id: 5
description: mapped name
"#,
        )
        .unwrap();

        let result = load_behavior_difference_files(dir.path()).unwrap();
        let expected =
            "[api_incompatibility] Private key password parameter name changed\nmapped name";
        assert_eq!(
            result
                .get("BD#private-key-password-renamed")
                .map(String::as_str),
            Some(expected)
        );
        assert_eq!(result.get("BD#5").map(String::as_str), Some(expected));
    }
}
