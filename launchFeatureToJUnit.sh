#!/bin/bash

# ================================
# Feature → JUnit XML Launcher (Enhanced)
# ================================

# Colors
RED=$(tput setaf 1)
GRN=$(tput setaf 2)
YEL=$(tput setaf 3)
BLU=$(tput setaf 4)
RST=$(tput sgr0)

# Defaults
FEATURE_DIR="features"
FEATURE_FILE=""
CONFIG_FILE="user_config.toml"
OUTPUT_FILE=""
JAR_FILE="target/featurefileToXML.jar"

# Help
usage() {
  echo "${YEL}Usage:${RST} ./launchFeatureToJUnit.sh [--feature <file>] [--config <file>] [--output <file>]"
  echo "         If no --feature is provided, all .feature files in '$FEATURE_DIR/' will be processed."
  echo ""
  echo "  ${BLU}--feature${RST}     Single feature file to convert (optional)"
  echo "  ${BLU}--config${RST}      TOML config file (default: user_config.toml)"
  echo "  ${BLU}--output${RST}      Output XML file (only valid with --feature)"
  echo ""
  exit 1
}

# Parse args
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --feature) FEATURE_FILE="$2"; shift ;;
        --config) CONFIG_FILE="$2"; shift ;;
        --output) OUTPUT_FILE="$2"; shift ;;
        -h|--help) usage ;;
        *) echo "${RED}❌ Unknown argument:${RST} $1"; usage ;;
    esac
    shift
done

# Function: Convert a single feature
convert_feature() {
    local feature="$1"
    local base=$(basename "$feature" .feature)
    local out="reports/${base}_junit_output.xml"
    local log="${out}.log"

    echo "${GRN}▶ Converting:${RST} $feature → $out"

    mkdir -p "$(dirname "$out")"

    START_TIME=$(date +%s)
    java -jar "$JAR_FILE" \
        --feature "$feature" \
        --config "$CONFIG_FILE" \
        --output "$out" > "$log" 2>&1
    END_TIME=$(date +%s)
    ELAPSED=$((END_TIME - START_TIME))

    echo "${GRN}✅ Done:${RST} $out (⏱️ ${ELAPSED}s, 📄 log: ${log})"
    echo ""
}

# === Run Mode: Single Feature ===
if [[ -n "$FEATURE_FILE" ]]; then
    if [[ ! -f "$FEATURE_FILE" ]]; then
        echo "${RED}❌ Feature file not found:${RST} $FEATURE_FILE"
        exit 1
    fi

    # Use --output if provided
    if [[ -n "$OUTPUT_FILE" ]]; then
        mkdir -p "$(dirname "$OUTPUT_FILE")"
        LOG_FILE="${OUTPUT_FILE}.log"
        echo "${GRN}▶ Converting single file:${RST} $FEATURE_FILE → $OUTPUT_FILE"

        START_TIME=$(date +%s)
        java -jar "$JAR_FILE" \
            --feature "$FEATURE_FILE" \
            --config "$CONFIG_FILE" \
            --output "$OUTPUT_FILE" > "$LOG_FILE" 2>&1
        END_TIME=$(date +%s)
        ELAPSED=$((END_TIME - START_TIME))

        echo "${GRN}✅ Done:${RST} $OUTPUT_FILE (⏱️ ${ELAPSED}s, 📄 log: ${LOG_FILE})"
    else
        convert_feature "$FEATURE_FILE"
    fi

# === Run Mode: Batch Convert ===
else
    echo "${YEL}🔍 No --feature provided. Searching in '${FEATURE_DIR}/'...${RST}"
    if [[ ! -d "$FEATURE_DIR" ]]; then
        echo "${RED}❌ Directory not found:${RST} $FEATURE_DIR"
        exit 1
    fi

    mapfile -t FEATURES < <(find "$FEATURE_DIR" -type f -name "*.feature")

    if [[ ${#FEATURES[@]} -eq 0 ]]; then
        echo "${RED}❌ No .feature files found in ${FEATURE_DIR}/.${RST}"
        exit 1
    fi

    echo "${GRN}🔧 Processing ${#FEATURES[@]} file(s)...${RST}"
    echo ""

    for f in "${FEATURES[@]}"; do
        convert_feature "$f"
    done

    echo "${GRN}✅ Batch conversion complete.${RST}"
fi
