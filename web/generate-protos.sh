#!/bin/bash
set -e;
mkdir -p protos
mkdir -p src/app/models/protos

cp ../server/app/protobuf/stakenet/orderbook/* protos/
sed -i '3s/.*/import "commands.proto";/' protos/api.proto
sed -i '4s/.*/import "events.proto";/' protos/api.proto
sed -i '3s/.*/import "models.proto";/' protos/commands.proto
sed -i '3s/.*/import "models.proto";/' protos/events.proto
PROTOC_GEN_TS_PATH="./node_modules/.bin/protoc-gen-ts" 
OUT_DIR="./src/app/models/protos" 
protoc \
    --plugin="protoc-gen-ts=${PROTOC_GEN_TS_PATH}" \
    --js_out="import_style=commonjs,binary:${OUT_DIR}" \
    --ts_out="./src/app/models/protos" \
    --proto_path="./protos" \
    api.proto commands.proto events.proto models.proto
