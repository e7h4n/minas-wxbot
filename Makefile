deploy:
	mvn clean package
	scp target/minas-wxbot-0.0.1-SNAPSHOT.jar aws:~/online/minas-wxbot/
	ssh aws 'sudo systemctl daemon-reload'
	ssh aws 'sudo systemctl restart minas-wxbot'
	ssh aws 'tail -f /var/log/minas-wxbot.log'
