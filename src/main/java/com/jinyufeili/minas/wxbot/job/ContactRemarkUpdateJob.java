package com.jinyufeili.minas.wxbot.job;

import com.jinyufeili.minas.wxbot.data.Resident;
import com.lostjs.wx4j.client.WxClient;
import com.lostjs.wx4j.data.response.Contact;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * Created by pw on 03/10/2016.
 */
@Component
public class ContactRemarkUpdateJob {

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

    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        List<Contact> contacts = wxClient.getContacts();

        for (int i = 0; i < contacts.size(); i++) {
            LOG.info("process use {}/{}", i, contacts.size());
            Contact contact = contacts.get(i);
            String nickName = contact.getNickName();
            List<Resident> residents = db.query(
                    "select wu.id as userId, r.name, rm.region, rm.building, rm.unit, rm.house_number" +
                            " from wechat_wechatuser wu" + " join auth_user au on wu.user_id = au.id" +
                            " left join crm_resident r on r.wechat_user_id = wu.id" +
                            " left join crm_room rm on rm.id = r.room_id" + " where au.first_name = :name;",
                    Collections.singletonMap("name", nickName), RESIDENT_ROW_MAPPER);

            Resident resident;
            if (CollectionUtils.isEmpty(residents)) {
                LOG.info("stranger, nickName={}", nickName);
            } else {
                String remarkName;
                if (residents.size() > 1) {
                    LOG.warn("重名用户 {}: {}", nickName, residents);
                    remarkName = "[D]" + nickName;
                } else {

                    resident = residents.get(0);

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
                }

                if (!contact.getRemarkName().equals(remarkName)) {
                    wxClient.updateRemarkName(contact.getUserName(), remarkName);
                }
            }
        }
    }
}
