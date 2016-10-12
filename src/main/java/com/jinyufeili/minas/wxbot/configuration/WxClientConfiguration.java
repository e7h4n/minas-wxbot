package com.jinyufeili.minas.wxbot.configuration;

import com.lostjs.wx4j.client.BasicWxClient;
import com.lostjs.wx4j.client.WxClient;
import com.lostjs.wx4j.context.FileWxContext;
import com.lostjs.wx4j.context.QRCodeWxContextSource;
import com.lostjs.wx4j.context.WxContext;
import com.lostjs.wx4j.context.WxContextSource;
import com.lostjs.wx4j.transporter.BasicWxTransporter;
import com.lostjs.wx4j.transporter.WxTransporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

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
    public WxContextSource wxContextSource(WxTransporter wxTransporter) {
        return new QRCodeWxContextSource(wxTransporter);
    }

    @Bean
    public WxClient wxClient(WxTransporter wxTransporter, WxContextSource wxContextSource, TaskExecutor taskExecutor) {
        BasicWxClient client = new BasicWxClient();

        client.setTransporter(wxTransporter);
        client.setContextSource(wxContextSource);

        taskExecutor.execute(() -> {
            client.syncCheckLoop();
        });

        return client;
    }
}