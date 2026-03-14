set -e
 
SRC_DIR="src"
OUT_DIR="out"
 
mkdir -p "$OUT_DIR"
 
echo "==> Compiling..."
find "$SRC_DIR" -name "*.java" | xargs javac -d "$OUT_DIR"
 
echo "==> Done. Run with:"
echo "    java -cp $OUT_DIR main.Main"