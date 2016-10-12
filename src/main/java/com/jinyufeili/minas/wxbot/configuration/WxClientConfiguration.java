package com.jinyufeili.minas.wxbot.configuration;

import com.lostjs.wx4j.client.BasicWxClient;
import com.lostjs.wx4j.client.WxClient;
import com.lostjs.wx4j.context.FileWxContext;
import com.lostjs.wx4j.context.QRCodeWxContextSource;
import com.lostjs.wx4j.context.WxContext;
import com.lostjs.wx4j.transporter.BasicWxTransporter;
import com.lostjs.wx4j.transporter.WxTransporter;
import com.lostjs.wx4j.utils.HttpUtil;
import org.apache.http.client.CookieStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by pw on 03/10/2016.
 */
@Configuration
public class WxClientConfiguration {

    @Value("${wxContext.file}")
    private String wxContextFile;

    @Bean
    public WxContext wxContext() {
        return new FileWxContext(wxContextFile);
    }

    @Bean
    public WxTransporter wxTransporter(WxContext wxContext) {
        return new BasicWxTransporter(wxContext);
    }

    @Bean
    public WxClient wxClient(WxTransporter wxTransporter) {
        BasicWxClient client = new BasicWxClient();

        client.setTransporter(wxTransporter);
        try {
            client.statusNotify();
        } catch (RuntimeException e) {
            wxTransporter.getWxContext().clear();

            boolean success = new QRCodeWxContextSource().initWxWebContext(wxTransporter.getWxContext());

            if (!success) {
                throw new RuntimeException("can't initialize wx context");
            }
            client.statusNotify();
        }
        client.startEventLoop();
        return client;
    }

    @Bean
    public HttpUtil httpUtil(CookieStore cookieStore) {
        HttpUtil httpUtil = new HttpUtil();
        httpUtil.setCookieStore(cookieStore);
        return httpUtil;
    }
}