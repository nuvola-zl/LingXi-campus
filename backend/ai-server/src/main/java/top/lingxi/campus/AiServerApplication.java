package top.lingxi.campus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "top.lingxi.campus")

@MapperScan({
        "top.lingxi.campus.**.mapper",
        "top.lingxi.campus.user.UserMapper"
})
@EnableScheduling
@EnableAsync
public class AiServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServerApplication.class, args);
    }

}
