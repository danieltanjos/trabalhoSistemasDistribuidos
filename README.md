# Corretora de Valores

Projeto pequeno com 3 modulos:

- `python/modulo1`: gateway TCP + simulador
- `java-core`: core com TCP + RMI
- `python/modulo3`: custodia gRPC

## Rodar

```bash
python3 -m pip install --target python/.deps grpcio grpcio-tools protobuf
PYTHONPATH=python/.deps python3 -m grpc_tools.protoc -Iproto --python_out=python/generated --grpc_python_out=python/generated proto/custodia.proto
curl -L https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.tar.gz -o /tmp/apache-maven-3.9.11-bin.tar.gz
tar -xzf /tmp/apache-maven-3.9.11-bin.tar.gz -C /tmp
/tmp/apache-maven-3.9.11/bin/mvn -Dmaven.repo.local=/tmp/m2repo -f java-core/pom.xml compile
export JAVA_CORE_CP="java-core/target/classes:$(find /tmp/m2repo -name '*.jar' | tr '\n' ':')"
```

Em terminais separados:

```bash
PYTHONPATH=python/.deps:python/generated python3 python/modulo3/custodia_server.py
java -cp "$JAVA_CORE_CP" br.senai.corretora.BrokerServerMain
python3 python/modulo1/gateway_server.py
python3 python/modulo1/simulador_corretoras.py
java -cp "$JAVA_CORE_CP" br.senai.corretora.MonitoringPanelMain
```
