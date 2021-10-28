# Alpine Linux with OpenJDK JRE
#FROM openjdk-tz-india:latest
FROM openjdk-11-headless-tz-india:latest

EXPOSE 6000 
# Debug port
EXPOSE 6030
#web port

RUN mkdir /mandi mandi/target mandi/bin mandi/tmp mandi/overrideProperties
COPY tmp/mandi-docker /mandi/
WORKDIR /mandi
RUN /usr/bin/crontab /mandi/crontab.txt  

ENV GOOGLE_APPLICATION_CREDENTIALS="./overrideProperties/config/humbhionline-firebase-adminsdk-6qe9f-ec2d486792.json"
CMD ["/bin/sh" , "/mandi/bin/service-start"]
