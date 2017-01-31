package com.jinyufeili.minas.wxbot.job;

import com.jinyufeili.minas.wxbot.data.Resident;
import com.lostjs.wx4j.client.WxClient;
import com.lostjs.wx4j.data.response.Contact;
import com.lostjs.wx4j.transporter.WxTransporter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.cookie.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
        r.setAvatarId(rs.getString("avatarId"));

        return r;
    });

    private Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private WxClient wxClient;

    @Autowired
    private WxTransporter wxTransporter;

    @Autowired
    private NamedParameterJdbcOperations db;

    @Scheduled(cron = "0 */30 * * * *")
    @PostConstruct
    public void run() {
        List<Contact> contacts = wxClient.getContacts();

        for (int i = 0; i < contacts.size(); i++) {
            Contact contact = contacts.get(i);
            LOG.info("process user {}/{}, name={}", i, contacts.size(), contact.getNickName());
            String nickName = contact.getNickName();
            List<Resident> residents = db.query(
                    "select wu.id as userId, r.name, rm.region, rm.building, rm.unit, rm.house_number, wu.avatarId" +
                            " from wechat_wechatuser wu" +
                            " join auth_user au on wu.user_id = au.id" +
                            " left join crm_resident r on r.wechat_user_id = wu.id" +
                            " left join crm_room rm on rm.id = r.room_id" +
                            " where au.first_name = :name;",
                    Collections.singletonMap("name", nickName), RESIDENT_ROW_MAPPER);

            if (residents == null) {
                LOG.info("stranger, nickName={}", nickName);
                continue;
            }

            if (residents.size() > 1) {
                LOG.info("潜在的重名问题，尝试拉取头像, nickname={}", contact.getNickName());
                String webAvatarUrl = String.format("%s&type=big", contact.getHeadImgUrl().replace("/cgi-bin/mmwebwx-bin", ""));
                String avatar = getMD5FromUrl(webAvatarUrl);
                residents = residents.stream().filter(r -> {
                    String residentAvatar = getMD5FromUrl(r.getAvatarId());
                    return avatar.equals(residentAvatar);
                }).collect(Collectors.toList());
            }

            Resident resident;
            if (CollectionUtils.isEmpty(residents)) {
                LOG.info("stranger, nickName={}", nickName);
                continue;
            }

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
                try {
                    wxClient.updateRemarkName(contact.getUserName(), remarkName);
                    LOG.info("update success");
                    Thread.sleep(TimeUnit.SECONDS.toMillis(20));
                } catch (Exception e) {
                    LOG.error("failed to update remark name, contact=" + contact.getUserName() + ", remarkName=" + remarkName, e);
                }
            }
        }
    }

    private String getMD5FromUrl(String url) {
        InputStream is = wxTransporter.getBinary(url);

        try {
            return DigestUtils.md5Hex(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
