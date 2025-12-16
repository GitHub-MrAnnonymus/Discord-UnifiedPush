#!/bin/bash

# Discord UnifiedPush Build Script
# Builds and signs both client (webview) and proxy variants

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
build_variant() {
    local variant=$1
    local build_type=$2

    echo -e "${GREEN}Building ${variant}${build_type}...${NC}"

    cd "$ANDROID_DIR"
    ./gradlew "assemble${variant}${build_type}" --no-daemon

    echo -e "${GREEN}${variant}${build_type} build complete!${NC}"
}

# Copy APKs to output directory
copy_apks() {
    local build_type=$1
    local build_type_lower=$(echo "$build_type" | tr '[:upper:]' '[:lower:]')

    mkdir -p "$OUTPUT_DIR"

    # Copy client APK
    if [[ -f "$ANDROID_DIR/app/build/outputs/apk/client/$build_type_lower/app-client-$build_type_lower.apk" ]]; then
        cp "$ANDROID_DIR/app/build/outputs/apk/client/$build_type_lower/app-client-$build_type_lower.apk" \
           "$OUTPUT_DIR/Discord-UnifiedPush-client-$build_type_lower.apk"
        echo -e "${GREEN}Copied: Discord-UnifiedPush-client-$build_type_lower.apk${NC}"
    fi

    # Copy proxy APK
    if [[ -f "$ANDROID_DIR/app/build/outputs/apk/proxy/$build_type_lower/app-proxy-$build_type_lower.apk" ]]; then
        cp "$ANDROID_DIR/app/build/outputs/apk/proxy/$build_type_lower/app-proxy-$build_type_lower.apk" \
           "$OUTPUT_DIR/Discord-UnifiedPush-proxy-$build_type_lower.apk"
        echo -e "${GREEN}Copied: Discord-UnifiedPush-proxy-$build_type_lower.apk${NC}"
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

    # Build both variants
    build_variant "Client" "$build_type"
    build_variant "Proxy" "$build_type"

    # Copy APKs to releases folder
    copy_apks "$build_type"

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
    echo "  debug   - Build debug APKs (no signing required)"
    echo "  release - Build release APKs (requires signing configuration)"
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
