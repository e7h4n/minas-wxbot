package com.jinyufeili.minas.wxbot.job;

import com.lostjs.wx4j.client.WxClient;
import com.lostjs.wx4j.data.response.Contact;
import com.lostjs.wx4j.data.response.GroupMember;
import com.lostjs.wx4j.exception.InvalidResponseException;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by pw on 03/10/2016.
 */
@Component
public class ContactAddJob {

    public static final int TOO_FREQ_WAIT_MINUTE = 30;

    @Value("${job.contactAdd.targetGroupName}")
    private String groupName;

    @Value("${job.contactAdd.reason}")
    private String reason;

    private int turn = 0;

    private int index = 0;

    private Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private WxClient wxClient;

    @Autowired
    private NamedParameterJdbcOperations db;

    @Scheduled(cron = "0 1,4,18,47 8-20/3 * * *")
    public void run() {
        List<Contact> contacts = wxClient.getContacts();
        Set<String> contactUserNameSet = contacts.stream().map(Contact::getUserName).collect(Collectors.toSet());
        Contact group = contacts.stream().filter(c -> c.getNickName().equals(groupName)).findAny().get();
        List<GroupMember> groupMembers = wxClient.getGroupMembers(group.getUserName());

        while (true) {
            if (index >= groupMembers.size()) {
                turn += 1;
                index = 0;
            }

            LOG.info("process use {}/{}", index, groupMembers.size());
            GroupMember groupMember = groupMembers.get(index);

            boolean isFriend = contactUserNameSet.contains(groupMember.getUserName());
            if (isFriend) {
                index++;
                continue;
            }

            boolean isAlreadySendRequest =
                    db.queryForObject("select count(*) from wechat_contact_request where nickname = :nickname",
                            Collections.singletonMap("nickname", groupMember.getNickName()), Integer.class) > turn;
            if (isAlreadySendRequest) {
                index++;
                continue;
            }

            try {
                if (processUser(groupMember)) {
                    index++;
                }

                break;
            } catch (RuntimeException e) {
                LOG.error("encounter an error and skip user {}, {}", group.getNickName(), e.getMessage());
            }
        }
    }

    private boolean processUser(GroupMember groupMember) {
        String nickName = groupMember.getNickName();
        LOG.info("add friend, nickName={}", nickName);

        boolean success = false;

        try {
            success = wxClient.addContact(groupMember.getUserName(), reason);
        } catch (InvalidResponseException e) {
            LOG.info("add contact failed, ret={}", e.getRet());
        }

        if (success) {
            MapSqlParameterSource source = new MapSqlParameterSource();
            source.addValue("nickname", groupMember.getNickName());
            source.addValue("createdTime", System.currentTimeMillis());
            db.update("insert into wechat_contact_request set nickname = :nickname, createdTime = :createdTime",
                    source);
        }

        return success;
    }
}