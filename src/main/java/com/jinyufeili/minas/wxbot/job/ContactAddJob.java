package com.jinyufeili.minas.wxbot.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostjs.wx4j.client.WxClient;
import com.lostjs.wx4j.data.response.Contact;
import com.lostjs.wx4j.data.response.GroupMember;
import com.lostjs.wx4j.exception.InvalidResponseException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Created by pw on 03/10/2016.
 */
@Component
@ConditionalOnProperty(value = "job.contactAdd.enabled")
public class ContactAddJob implements CommandLineRunner {

    @Value("${job.contactAdd.targetGroupName}")
    private String groupName;

    @Value("${job.contactAdd.reason}")
    private String reason;

    @Value("${job.contactAdd.cacheFile}")
    private String cacheFile;

    private Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private WxClient wxClient;

    public void run(String[] args) throws IOException {
        File file = new File(cacheFile);
        String fileContent = FileUtils.readFileToString(file);
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> friendRequests = objectMapper.readValue(fileContent, new TypeReference<List<String>>() {

        });

        List<Contact> contacts = wxClient.getContacts();
        Set<String> contactUserNameSet = contacts.stream().map(Contact::getUserName).collect(Collectors.toSet());

        Contact group = contacts.stream().filter(c -> c.getNickName().equals(groupName)).findAny().get();
        List<GroupMember> groupMembers = wxClient.getGroupMembers(group.getUserName());

        int fib1 = 0;
        int fib2 = 1;
        for (int i = 0; i < groupMembers.size(); i++) {
            LOG.info("process use {}/{}", i, groupMembers.size());
            GroupMember g = groupMembers.get(i);
            String nickName = g.getNickName();
            try {
                boolean isFriend = contactUserNameSet.contains(g.getUserName());
                if (isFriend) {
                    continue;
                }

                if (friendRequests.stream().filter(f -> f.equals(nickName)).findAny().isPresent()) {
                    LOG.info("ignore user {}, already send request", nickName);
                    continue;
                }

                LOG.info("add friend, nickName={}", nickName);

                boolean success = false;
                try {
                    success = wxClient.addContact(g.getUserName(), reason);
                } catch (InvalidResponseException e) {
                    if (e.getRet() == 1205) {
                        int sleepMinute = fib1 + fib2;
                        LOG.info("add friend too fast, sleep {} minutes to retry", sleepMinute);
                        try {
                            Thread.sleep(TimeUnit.MINUTES.toMillis(sleepMinute));
                        } catch (InterruptedException e1) {
                            throw new RuntimeException(e1);
                        }

                        fib1 = fib2;
                        fib2 = sleepMinute;
                    } else {
                        throw new RuntimeException(e);
                    }
                }

                if (success) {
                    fib1 = 0;
                    fib2 = 1;
                    friendRequests.add(nickName);

                    try {
                        FileUtils.writeStringToFile(file, objectMapper.writeValueAsString(friendRequests));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                try {
                    Thread.sleep(TimeUnit.MINUTES.toMillis(1));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } catch (RuntimeException e) {
                LOG.error("error when add friend " + nickName, e);
            }
        }
    }
}