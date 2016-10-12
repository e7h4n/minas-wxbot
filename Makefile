deploy:
	mvn clean package
	scp target/minas-wxbot-0.0.1-SNAPSHOT.jar bcc:~/online/minas-wxbot/
	#service minas-wxbot restart
