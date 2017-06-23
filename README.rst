Spark TF-IDF index example
=======================

Build with command:
sbt package

Then run:

YOUR_SPARK_HOME/bin/spark-submit \
  --class "TfIdfExample" \
  --master local[4] \
  target/scala-2.11/spark-tf-idf_2.11-1.0.jar \
  path/to/files/with/documents \
  20 \
  path/where/index/should/be/stored

20 - is a number of related documents for each word. May be changed.

