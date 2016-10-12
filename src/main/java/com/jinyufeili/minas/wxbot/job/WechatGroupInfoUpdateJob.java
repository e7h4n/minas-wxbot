package com.jinyufeili.minas.wxbot.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinyufeili.minas.wxbot.data.Resident;
import com.lostjs.wx4j.client.WxClient;
import com.lostjs.wx4j.data.response.Contact;
import com.lostjs.wx4j.data.response.GroupMember;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by pw on 03/10/2016.
 */
@Component
@ConditionalOnProperty(value = "job.wechatGroupInfoUpdate")
public class WechatGroupInfoUpdateJob implements CommandLineRunner {

    private static final RowMapper<Resident> RESIDENT_ROW_MAPPER = ((rs, i) -> {
        Resident r = new Resident();

        r.setUserId(rs.getInt("userId"));
        r.setName(rs.getString(("name")));
        r.setRegion(rs.getInt("region"));
        r.setBuilding(rs.getInt("building"));
        r.setUnit(rs.getInt("unit"));
        r.setHouseNumber(rs.getInt("house_number"));
        return r;
    });

    private Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private WxClient wxClient;

    @Autowired
    private NamedParameterJdbcOperations db;

    public void run(String[] args) throws IOException {
        File file = new File("/Users/pw/workspace/minas-wxbot/friend-requests");
        String fileContent = FileUtils.readFileToString(file);
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> friendRequests = objectMapper.readValue(fileContent, new TypeReference<List<String>>() {
        });

        List<Contact> contacts = wxClient.getContacts();

        Contact group = contacts.stream().filter(c -> c.getNickName().equals("翡丽铂庭 业主之家")).findAny().get();
        List<GroupMember> groupMembers = wxClient.getGroupMembers(group.getUserName());

        List<String> userNames = groupMembers.stream().map(g -> g.getUserName()).collect(Collectors.toList());

        List<Contact> groupContacts = new ArrayList<>();
        for (int i = 0; i < Math.ceil(userNames.size() / 50.0); i++) {
            LOG.info("get group users {} - {}", i * 50, i * 50 + 50);
            List<String> subUserNames = userNames.subList(i * 50, Math.min(userNames.size(), i * 50 + 50));
            groupContacts.addAll(wxClient.getContactsByUserNames(subUserNames));
        }

        Map<String, Boolean> friendStatusMap = groupContacts.stream().collect(
                Collectors.toMap(Contact::getUserName, c -> StringUtils.isNotBlank(c.getNickName())));

        Map<String, Contact> contactMap = groupContacts.stream().collect(
                Collectors.toMap(Contact::getUserName, Function.identity()));

        for (int i = 0; i < groupMembers.size(); i++) {
            GroupMember g = groupMembers.get(i);
            LOG.info("process use {}/{}", i, groupMembers.size());
            try {
                boolean isFriend = friendStatusMap.get(g.getUserName());
                String nickName = isFriend ? contactMap.get(g.getUserName()).getNickName() : g.getNickName();
                List<Resident> residents = db.query(
                        "select wu.id as userId, r.name, rm.region, rm.building, rm.unit, rm.house_number" +
                                " from wechat_wechatuser wu" +
                                " join auth_user au on wu.user_id = au.id" +
                                " left join crm_resident r on r.wechat_user_id = wu.id" +
                                " left join crm_room rm on rm.id = r.room_id" +
                                " where au.first_name = :name;",
                        Collections.singletonMap("name", nickName),
                        RESIDENT_ROW_MAPPER);

                Resident resident = null;
                if (CollectionUtils.isEmpty(residents)) {
                    LOG.info("stranger, nickName={}", nickName);
                } else {
                    if (residents.size() > 1) {
                        LOG.warn("重名用户 {}: {}", nickName, residents);
                    }

                    resident = residents.get(0);

                    String remarkName;
                    if (StringUtils.isNotBlank(resident.getName())) {
                        LOG.info("userId={}, nickName={}, resident={}", resident.getUserId(), nickName,
                                resident.getName());

                        String houseNumber;
                        if (resident.getRegion() == 1) {
                            houseNumber = String.format("1-%d-%d", resident.getBuilding(), resident.getHouseNumber());
                        } else {
                            houseNumber = String.format("2-%d-%d-%d", resident.getBuilding(), resident.getUnit(),
                                    resident.getHouseNumber());
                        }

                        remarkName = String.format("✅ %s %s", houseNumber, resident.getName());
                    } else {
                        remarkName = String.format("❓ %s", nickName);
                        LOG.info("unverified user, userId={}, nickName={}", resident.getUserId(), nickName);
                    }

                    if (isFriend) {
                        if (!g.getNickName().equals(remarkName)) {
                            wxClient.updateRemarkName(g.getUserName(), remarkName);
                        }
                    }
                }

                if (!isFriend) {
                    if (friendRequests.stream().filter(f -> f.equals(nickName)).findAny().isPresent()) {
                        LOG.info("ignore user {}, already send request", nickName);
                        continue;
                    }

                    LOG.info("add friend, nickName={}", nickName);

                    boolean success = wxClient.addContact(g.getUserName(), "翡丽大群业主身份验证，请通过");

                    if (success) {
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
                }
            } catch (RuntimeException e) {
                LOG.error("exception when add contact " + g.getNickName(), e);
            }
        }
    }
}
