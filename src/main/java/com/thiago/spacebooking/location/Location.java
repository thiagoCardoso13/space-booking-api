package com.thiago.spacebooking.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    protected Location() {
    }

    public Location(String name, String address, String city, LocalTime opensAt, LocalTime closesAt) {
        this.name = name;
        this.address = address;
        this.city = city;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }
}
