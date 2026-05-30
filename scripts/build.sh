#!/bin/bash

# Build and push Docker image script
# Usage: ./scripts/build.sh <version>
# Example: ./scripts/build.sh 2.0.0

set -e

if [ -z "$1" ]; then
    echo "Error: Version tag is required"
    echo "Usage: ./scripts/build.sh <version>"
    echo "Example: ./scripts/build.sh 2.0.0"
    exit 1
fi

VERSION=$1
IMAGE_NAME="davismariotti/campalert"

echo "Building Docker image..."
echo "Image: ${IMAGE_NAME}"
echo "Version: ${VERSION}"
echo ""

# Check if buildx builder exists, create if not
if docker buildx inspect multiarch-builder >/dev/null 2>&1; then
    echo "Using existing buildx builder..."
    docker buildx use multiarch-builder
    docker buildx inspect multiarch-builder
    echo ""
else
    echo "Creating buildx builder for multi-arch builds..."
    docker buildx create --use --name multiarch-builder
    docker buildx inspect --bootstrap

    if [ $? -ne 0 ]; then
        echo ""
        echo "Error: Failed to bootstrap buildx builder"
        echo "Try running: docker buildx rm multiarch-builder"
        echo "Then run this script again"
        exit 1
    fi
    echo ""
fi

# Verify the builder supports required platforms
BUILDER_INFO=$(docker buildx inspect multiarch-builder 2>&1)
if ! echo "$BUILDER_INFO" | grep -q "linux/amd64"; then
    echo ""
    echo "Error: buildx builder doesn't support linux/amd64"
    echo "Try running: docker buildx rm multiarch-builder"
    echo "Then run this script again"
    exit 1
fi

if ! echo "$BUILDER_INFO" | grep -q "linux/arm64"; then
    echo ""
    echo "Error: buildx builder doesn't support linux/arm64"
    echo "Try running: docker buildx rm multiarch-builder"
    echo "Then run this script again"
    exit 1
fi

echo "Building multi-arch image for linux/amd64 and linux/arm64..."
echo ""

read -p "Build multi-arch image? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Building ${IMAGE_NAME}:${VERSION} and ${IMAGE_NAME}:latest for linux/amd64 and linux/arm64..."
    echo ""

    docker buildx build \
        --platform linux/amd64,linux/arm64 \
        --build-arg VERSION=${VERSION} \
        -t ${IMAGE_NAME}:${VERSION} \
        -t ${IMAGE_NAME}:latest \
        --progress=plain \
        .

    if [ $? -ne 0 ]; then
        echo ""
        echo "Error: Build failed"
        exit 1
    fi

    echo ""
    echo "Build complete!"
    echo ""

    read -p "Push to Docker Hub? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Pushing ${IMAGE_NAME}:${VERSION}..."
        docker buildx build \
            --platform linux/amd64,linux/arm64 \
            --build-arg VERSION=${VERSION} \
            -t ${IMAGE_NAME}:${VERSION} \
            -t ${IMAGE_NAME}:latest \
            --push \
            .

        echo ""
        echo "Successfully pushed multi-arch images:"
        echo "  - ${IMAGE_NAME}:${VERSION}"
        echo "  - ${IMAGE_NAME}:latest"
        echo "  Platforms: linux/amd64, linux/arm64"
    else
        echo "Skipped push to Docker Hub"
    fi
else
    echo "Skipped build"
    echo ""
    echo "To build locally for a single platform:"
    echo "  docker build --platform linux/amd64 --build-arg VERSION=${VERSION} -t ${IMAGE_NAME}:${VERSION} ."
fi
