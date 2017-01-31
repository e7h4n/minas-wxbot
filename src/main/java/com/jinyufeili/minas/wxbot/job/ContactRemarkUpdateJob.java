package com.jinyufeili.minas.wxbot.job;

import com.jinyufeili.minas.wxbot.data.Resident;
import com.lostjs.wx4j.client.WxClient;
import com.lostjs.wx4j.data.response.Contact;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
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
    private CookieStore cookieStore;

    @Autowired
    private WxClient wxClient;

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
                String webAvatarUrl = String.format("https://wx.qq.com%s&type=big", contact.getHeadImgUrl());
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
        LOG.info("get avatar from: {}", url);
        GetRequest request = Unirest.get(url);

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        LOG.info("uri domain: {}", uri.getHost());
        List<Cookie> cookies = cookieStore.getCookies();
        LOG.info("cookie size: {}", cookies.size());
        cookies = cookies.stream().filter(c -> {
            LOG.info("cookie {} domain {}", c.getName(), c.getDomain());
            return c.getDomain().equals(uri.getHost());
        }).collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(cookies)) {
            String cookie = cookies.stream().map(c -> String.format("%s=%s", c.getName(), c.getValue()))
                    .collect(Collectors.joining("; "));

            LOG.info("cookie: {}", cookie);
            request.header("Cookie", cookie);
        }

        HttpResponse<InputStream> webAvatar;
        try {
            webAvatar = request.asBinary();
        } catch (UnirestException e) {
            throw new RuntimeException(e);
        }

        return getMD5(webAvatar.getBody());
    }

    private String getMD5(InputStream inputStream) {
        try {
            return DigestUtils.md5Hex(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
