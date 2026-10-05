package com.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "address")
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer number;
    @Column(nullable = false, columnDefinition = "text")
    private String street;
    @Column(nullable = false, columnDefinition = "text")
    private String neighborhood;

    public Address(Integer number, String street, String neighborhood) {
        this.number = number;
        this.street = street;
        this.neighborhood = neighborhood;
    }

    public Address() {
    }
}
