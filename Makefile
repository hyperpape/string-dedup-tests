all: docker-start
	mvn install
	java -cp target/benchmarks.jar com.justinblank.StringDeduplicationTest churntest > ~/tmp/string-deduplication-churn 2>&1
	java -cp target/benchmarks.jar com.justinblank.StringDeduplicationTest > ~/tmp/string-deduplication-output 2>&1

docker-start:
	sudo service docker start

analyze:
	egrep '(Parameters)|(TotalSeen)|(VM options)' ~/tmp/string-deduplication-churn | ./extract_results.py > ~/tmp/string-deduplication-churn-processed.org
	egrep '(Parameters)|(TotalSeen)|(VM options)' ~/tmp/string-deduplication-output | ./extract_results.py > ~/tmp/string-deduplication-output-processed.org
