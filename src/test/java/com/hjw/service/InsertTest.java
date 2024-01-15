package com.hjw.service;


import com.hjw.model.domain.User;
import com.hjw.service.impl.UserServiceImpl;
import javafx.scene.paint.Stop;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;

import java.util.ArrayList;

@SpringBootTest
public class InsertTest
{

    @Autowired
    private UserService userService;

    @Test
    void insert()
    {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();


        final int INSERT_NUM = 200000;
        ArrayList<User> list = new ArrayList<>();
        for (int i = 0; i < INSERT_NUM; i++)
        {
            User user = new User();
            user.setUserAccount("hjw123");
            user.setUserPassword("12345678");
            user.setPhone("1235345346567");
            user.setGender(1);
            user.setEmail("22334234@qq.com");
            user.setPlanetCode(4 + i + "");
            user.setUsername("jtqifei");
            user.setAvatarUrl("https://pic.netbian.com/uploads/allimg/231129/012611-1701192371980a.jpg");
            user.setTags("[\"男\",\"java\"]");
            user.setUserRole(0);
            user.setUserStatus(0);
            list.add(user);
        }
        userService.saveBatch(list, 2);

        stopWatch.stop();
        System.out.println(stopWatch.getTotalTimeMillis());


    }
}
