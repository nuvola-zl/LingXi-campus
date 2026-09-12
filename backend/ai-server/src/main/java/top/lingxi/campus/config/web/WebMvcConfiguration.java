package top.lingxi.campus.config.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import top.lingxi.campus.common.interceptor.JwtTokenInterceptor;
import top.lingxi.campus.common.json.JacksonObjectMapper;


import java.util.List;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {


    private final JwtTokenInterceptor jwtTokenInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册统一 JWT 拦截器...");

        registry.addInterceptor(jwtTokenInterceptor)
                .addPathPatterns(
                        "/api/**",
                        "/admin/**",
                        "/hr/**",
                        "/dag/**"
                )
                .excludePathPatterns(
                        "/api/v1/user/login",
                        "/user/register",
                        "/v1/api-docs/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/doc.html",
                        "/webjars/**",
                        "/favicon.ico"
                );
    }

    /**
     * 配置静态资源映射
     * @param registry
     */

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("开始配置静态资源映射...");
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/");
    }

    /**
     * 通过knife4j生成接口文档的相关配置 (OpenAPI 3.0)
     * @return
     */
    @Bean
    public OpenAPI customOpenAPI() {
        log.info("准备生成接口文档");
        return new OpenAPI()
                .info(new Info()
                        .title("AI-Hub项目接口文档")
                        .version("2.0")
                        .description("AI-Hub项目接口测试文档"));
    }

    /**
     * 调整返回的时间格式
     * @param converters
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters){
        //创建一个消息转换器对象
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

        //需要为消息转换器设置一个对象转换器，对象转换器可以将java对象序列化为json数据
        converter.setObjectMapper(new JacksonObjectMapper());
        //把自己的消息转换器加到converter容器,并把自己的消息转换器优先级放到最高
        converters.add(0,converter);
    }


}
