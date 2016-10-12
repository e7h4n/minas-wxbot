package com.jinyufeili.minas.wxbot.job;

import com.lostjs.wx4j.client.WxClient;
import com.lostjs.wx4j.data.response.Contact;
import com.lostjs.wx4j.data.response.GroupMember;
import com.lostjs.wx4j.exception.InvalidResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
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

    public static final int TOO_FREQ_WAIT_MINUTE = 30;

    @Value("${job.contactAdd.targetGroupName}")
    private String groupName;

    @Value("${job.contactAdd.reason}")
    private String reason;

    private int turn = 0;

    private Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private WxClient wxClient;

    @Autowired
    private NamedParameterJdbcOperations db;

    public void run(String[] args) throws IOException {

        while (true) {
            List<Contact> contacts = wxClient.getContacts();
            Set<String> contactUserNameSet = contacts.stream().map(Contact::getUserName).collect(Collectors.toSet());

            Contact group = contacts.stream().filter(c -> c.getNickName().equals(groupName)).findAny().get();
            List<GroupMember> groupMembers = wxClient.getGroupMembers(group.getUserName());

            for (int i = 0; i < groupMembers.size(); i++) {
                LOG.info("process use {}/{}", i, groupMembers.size());
                GroupMember groupMember = groupMembers.get(i);
                boolean isFriend = contactUserNameSet.contains(groupMember.getUserName());
                if (isFriend) {
                    continue;
                }

                boolean isAlreadySendRequest =
                        db.queryForObject("select count(*) from wechat_contact_request where nickname = :nickname",
                                Collections.singletonMap("nickname", groupMember.getNickName()), Integer.class) > turn;
                if (isAlreadySendRequest) {
                    continue;
                }

                try {
                    processUser(groupMember);
                } catch (RuntimeException e) {
                    LOG.error("encounter an error and skip user {}, {}", group.getNickName(), e.getMessage());
                }
            }

            turn += 1;
        }
    }

    private void processUser(GroupMember groupMember) {
        String nickName = groupMember.getNickName();
        LOG.info("add friend, nickName={}", nickName);

        boolean success = false;
        while (!success) {
            success = internalAddContact(groupMember);
        }
    }

    private boolean internalAddContact(GroupMember groupMember) {
        boolean success;

        try {
            success = wxClient.addContact(groupMember.getUserName(), reason);
        } catch (InvalidResponseException e) {
            if (e.getRet() == 1205) {
                LOG.info("add friend too fast, sleep {} minutes to retry", TOO_FREQ_WAIT_MINUTE);
                sleep(TimeUnit.MINUTES.toMillis(TOO_FREQ_WAIT_MINUTE));
                return false;
            } else {
                throw new RuntimeException(e);
            }
        }

        if (success) {
            MapSqlParameterSource source = new MapSqlParameterSource();
            source.addValue("nickname", groupMember.getNickName());
            source.addValue("createdTime", System.currentTimeMillis());
            db.update("insert into wechat_contact_request set nickname = :nickname, createdTime = :createdTime",
                    source);
        }

        return true;
    }

    private void sleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e1) {
            throw new RuntimeException(e1);
        }
    }
}