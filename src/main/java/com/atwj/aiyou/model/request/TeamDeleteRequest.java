package com.atwj.aiyou.model.request;

import lombok.Data;

import java.io.Serializable;

@Data
public class TeamDeleteRequest implements Serializable {

    private static final long serialVersionUID = -5860707094194210842L;

    private long id;
}
