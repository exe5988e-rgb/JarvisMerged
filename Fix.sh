#!/usr/bin/env bash

set -e

echo "🔧 Fixing Kotlin source directories..."

PROJECT_ROOT="$(pwd)"

find "$PROJECT_ROOT" -type f -name "*.kt" | while read -r kt_file; do
    # Only act on files inside src/main/java
    if [[ "$kt_file" == *"/src/main/java/"* ]]; then
        new_file="${kt_file/src\/main\/java/src\/main\/kotlin}"

        new_dir="$(dirname "$new_file")"

        echo "➡ Moving:"
        echo "   $kt_file"
        echo "   → $new_file"

        mkdir -p "$new_dir"
        mv "$kt_file" "$new_file"
    fi
done

echo "🧹 Removing empty java directories (if any)..."
find "$PROJECT_ROOT" -type d -path "*/src/main/java*" -empty -delete

echo "✅ Kotlin source structure fixed."
