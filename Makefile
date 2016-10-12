deploy:
	mvn clean package
	scp target/minas-wxbot-0.0.1-SNAPSHOT.jar bcc:~/online/minas-wxbot/
	ssh bcc 'sudo systemctl restart minas-wxbot'
	ssh bcc 'tail -f /var/log/minas-wxbot.log'
