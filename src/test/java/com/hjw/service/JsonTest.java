package com.hjw.service;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class JsonTest
{
    @Test
    void test()
    {
        // json
        String tag = " [\"java\",\"男\",\"编程\"]";

        Gson gson = new Gson();
        List<String> tagList = gson.fromJson(tag, new TypeToken<List<String>>()
        {
        }.getType());

        System.out.println(tagList);

    }

    @Test
    void test1()
    {

    }
}
