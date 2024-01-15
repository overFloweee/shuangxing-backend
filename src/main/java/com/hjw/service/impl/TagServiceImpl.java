package com.hjw.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hjw.mapper.TagMapper;
import com.hjw.model.domain.Tag;
import com.hjw.service.TagService;
import org.springframework.stereotype.Service;

/**
 * @author 86157
 * @description 针对表【tag(标签)】的数据库操作Service实现
 * @createDate 2023-12-26 19:04:36
 */
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService
{

}




