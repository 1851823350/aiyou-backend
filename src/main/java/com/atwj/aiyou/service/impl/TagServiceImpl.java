package com.atwj.aiyou.service.impl;

import com.atwj.aiyou.model.domain.Tag;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.atwj.aiyou.service.TagService;
import com.atwj.aiyou.mapper.TagMapper;
import org.springframework.stereotype.Service;

/**
* @author blablablala
* @description 针对表【tag】的数据库操作Service实现
* @createDate 2023-04-25 20:53:24
*/
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag>
    implements TagService{

}




