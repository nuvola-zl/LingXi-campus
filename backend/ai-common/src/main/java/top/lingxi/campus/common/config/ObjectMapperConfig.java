package top.lingxi.campus.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import top.lingxi.campus.common.json.JacksonObjectMapper;


@Configuration
public class ObjectMapperConfig {

    @Bean
    @Primary  // 标记为主要的 ObjectMapper，Spring 优先用这个
    public JacksonObjectMapper jacksonObjectMapper() {
        return new JacksonObjectMapper();
    }
}