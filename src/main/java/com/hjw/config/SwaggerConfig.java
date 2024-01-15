package com.hjw.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;

// 开启 swagger 功能
@EnableSwagger2WebMvc
// 配置类
@Configuration
public class SwaggerConfig
{

    @Bean
    public Docket createConfig()
    {
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo())
                .select()
                // 这里要标注控制器的位置
                .apis(RequestHandlerSelectors.basePackage("com.hjw.controller"))
                .paths(PathSelectors.any())
                .build();
    }


    private ApiInfo apiInfo()
    {
        return new ApiInfoBuilder()
                .title("双星伙伴匹配系统")
                .description("双星伙伴匹配系统接口文档")
                .termsOfServiceUrl("https://gitee.com/ququbudu")
                .version("1.0")
                .build();
    }


}
