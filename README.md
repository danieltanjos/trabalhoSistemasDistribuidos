# Corretora de Valores

Projeto pequeno com 3 modulos:

- `java-core`: gateway TCP + simulador + core + custodia + painel

## Rodar

```bash
curl -L https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.tar.gz -o /tmp/apache-maven-3.9.11-bin.tar.gz
tar -xzf /tmp/apache-maven-3.9.11-bin.tar.gz -C /tmp
/tmp/apache-maven-3.9.11/bin/mvn -Dmaven.repo.local=/tmp/m2repo -f java-core/pom.xml compile
export JAVA_CORE_CP="java-core/target/classes:$(find /tmp/m2repo -name '*.jar' | tr '\n' ':')"
```

Demo simples com um comando:

```bash
java -cp "$JAVA_CORE_CP" br.senai.corretora.DemoMain
```

Modo manual:

```bash
java -cp "$JAVA_CORE_CP" br.senai.corretora.CustodyServerMain
java -cp "$JAVA_CORE_CP" br.senai.corretora.BrokerServerMain
java -cp "$JAVA_CORE_CP" br.senai.corretora.GatewayServerMain
java -cp "$JAVA_CORE_CP" br.senai.corretora.BrokerSimulationMain
java -cp "$JAVA_CORE_CP" br.senai.corretora.MonitoringPanelMain
```
