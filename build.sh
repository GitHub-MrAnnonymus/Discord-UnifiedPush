#!/bin/bash

# Discord UnifiedPush Build Script
# Builds and signs a single APK; notification target (WebView vs external
# Discord-compatible app) is configurable at runtime in-app.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR/Android"
OUTPUT_DIR="$SCRIPT_DIR/releases"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Discord UnifiedPush Build Script ===${NC}"

# Check if signing is configured
check_signing() {
    if [[ -f "$ANDROID_DIR/keystore.properties" ]]; then
        echo -e "${GREEN}Found keystore.properties${NC}"
        return 0
    elif [[ -n "$KEYSTORE_FILE" && -n "$KEYSTORE_PASSWORD" && -n "$KEY_ALIAS" && -n "$KEY_PASSWORD" ]]; then
        echo -e "${GREEN}Using environment variables for signing${NC}"
        return 0
    else
        echo -e "${YELLOW}Warning: No signing configuration found.${NC}"
        echo "For signed builds, either:"
        echo "  1. Create Android/keystore.properties with:"
        echo "     storeFile=path/to/your.keystore"
        echo "     storePassword=your_store_password"
        echo "     keyAlias=your_key_alias"
        echo "     keyPassword=your_key_password"
        echo "  2. Or set environment variables:"
        echo "     KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD"
        echo ""
        return 1
    fi
}

# Build function
build_app() {
    local build_type=$1

    echo -e "${GREEN}Building ${build_type}...${NC}"

    cd "$ANDROID_DIR"
    ./gradlew "assemble${build_type}" --no-daemon

    echo -e "${GREEN}${build_type} build complete!${NC}"
}

# Copy APK to output directory
copy_apk() {
    local build_type=$1
    local build_type_lower=$(echo "$build_type" | tr '[:upper:]' '[:lower:]')

    mkdir -p "$OUTPUT_DIR"

    local src="$ANDROID_DIR/app/build/outputs/apk/$build_type_lower/app-$build_type_lower.apk"
    if [[ -f "$src" ]]; then
        cp "$src" "$OUTPUT_DIR/Discord-UnifiedPush-$build_type_lower.apk"
        echo -e "${GREEN}Copied: Discord-UnifiedPush-$build_type_lower.apk${NC}"
    else
        echo -e "${YELLOW}APK not found at $src${NC}"
    fi
}

# Main execution
main() {
    local build_type="${1:-Release}"

    # Capitalize first letter
    build_type="$(tr '[:lower:]' '[:upper:]' <<< ${build_type:0:1})${build_type:1}"

    echo "Build type: $build_type"
    echo ""

    # Check signing for release builds
    if [[ "$build_type" == "Release" ]]; then
        if ! check_signing; then
            echo -e "${YELLOW}Building debug instead...${NC}"
            build_type="Debug"
        fi
    fi

    echo ""

    build_app "$build_type"
    copy_apk "$build_type"

    echo ""
    echo -e "${GREEN}=== Build Complete ===${NC}"
    echo "APKs are in: $OUTPUT_DIR"
    ls -la "$OUTPUT_DIR"/*.apk 2>/dev/null || echo "No APKs found"
}

# Show usage
usage() {
    echo "Usage: $0 [debug|release]"
    echo ""
    echo "Options:"
    echo "  debug   - Build debug APK (no signing required)"
    echo "  release - Build release APK (requires signing configuration)"
    echo ""
    echo "Default: release"
}

# Handle arguments
case "${1:-}" in
    -h|--help)
        usage
        exit 0
        ;;
    *)
        main "$@"
        ;;
esac
