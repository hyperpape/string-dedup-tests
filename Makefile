all:
	mvn install
	java -cp target/benchmarks.jar com.justinblank.StringDeduplicationTest > output 2>&1
