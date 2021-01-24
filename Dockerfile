# Alpine Linux with OpenJDK JRE
FROM openjdk-tz-india:latest
EXPOSE 6000 
# Debug port
EXPOSE 6030
#web port

RUN mkdir /mandi mandi/target mandi/bin mandi/tmp mandi/overrideProperties
COPY tmp/mandi-docker /mandi/
WORKDIR /mandi
RUN /usr/bin/crontab /mandi/crontab.txt  && crond -bS 
CMD ["/usr/bin/java","-Xmx4g","-XX:+HeapDumpOnOutOfMemoryError","-XX:HeapDumpPath=tmp/java.hprof","-XX:-OmitStackTraceInFastThrow","-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=6000", "-DPORT=6030","-Dswf.env=production","-Dswf.pidfile=tmp/pid","-Dderby.locks.waitTimeout=10","-DSystem.out.close=true","-DSystem.err.close=true","-DSystem.in.close=true","-cp","overrideProperties/:target/classes/:target/dependency/swf-plugin-mobilesignup-2.6-SNAPSHOT.jar:target/dependency/swf-plugin-ecommerce-2.6-SNAPSHOT.jar:target/dependency/swf-plugin-collab-2.6-SNAPSHOT.jar:target/dependency/*","com.venky.swf.JettyServer" ]
