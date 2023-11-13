test:
	mvn -f pom.xml test

build:
	mvn -DskipTests=true -f pom.xml clean install
	mv target/jvarler-*-jar-with-dependencies.jar target/jvarler.jar

clean:
	mvn -DskipTests=true -f pom.xml clean

clean-samples:
	rm -rf samples/**/generated

clean-all: clean clean-samples

sample=001
args=
run-sample:
	java -jar ./target/jvarler.jar $(args) \
		-e samples/$(sample)/generated/exports.json \
		-d samples/$(sample)/destinations.yaml \
		-c samples/$(sample)/vars.yaml

run:
	java -jar ./target/jvarler.jar $(args)

