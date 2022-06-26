SIZES="0 16 256 65536"
STRING_COUNT="1 1024 65536"

for size in $SIZES
do
    for string_count in $STRING_COUNT
    do
	echo "Doing $size $string_count false"
	java -Xmx1024m -cp target/StringDeduplication-1.0-SNAPSHOT.jar com.justinblank.StringDeduplicationTest "$size" "$string_count" false
	echo "Doing $size $string_count true"
	java -Xmx1024m -cp target/StringDeduplication-1.0-SNAPSHOT.jar com.justinblank.StringDeduplicationTest "$size" "$string_count" true
    done
done
