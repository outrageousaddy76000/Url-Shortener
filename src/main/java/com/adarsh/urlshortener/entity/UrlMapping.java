package com.adarsh.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name="url_mapping")
public class UrlMapping {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false,length=2048,unique=true)
    private String originalUrl;

    @Column(nullable=false,unique=true)
    private String shortCode;

    @Column(unique=true)
    private String alias;
}
