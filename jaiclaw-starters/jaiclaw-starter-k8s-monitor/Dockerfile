FROM eclipse-temurin:21-jre

# Install kubectl for raw kubectl_exec tool support
RUN apt-get update && apt-get install -y curl && \
    curl -LO "https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" && \
    chmod +x kubectl && mv kubectl /usr/local/bin/ && \
    apt-get remove -y curl && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

COPY target/*.jar /app/k8s-monitor.jar

ENV KUBECONFIG=/root/.kube/config

ENTRYPOINT ["java", "-jar", "/app/k8s-monitor.jar"]
