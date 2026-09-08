FROM fluent/fluentd:v1.16-debian-1

# Switch to root to install gems
USER root

# Install fluent-plugin-splunk-hec
RUN gem install fluent-plugin-splunk-hec --no-document

USER fluent
