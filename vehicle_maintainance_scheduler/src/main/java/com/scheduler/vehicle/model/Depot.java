package com.scheduler.vehicle.model;

/*
 -Represents a vehicle maintenance depot
 - Contains depot ID and available mechanic-hours
 */
public class Depot {
    private Integer id;
    private Integer mechanicHours;

    public Depot() {}

    public Depot(Integer id, Integer mechanicHours) {
        this.id = id;
        this.mechanicHours = mechanicHours;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMechanicHours() {
        return mechanicHours;
    }

    public void setMechanicHours(Integer mechanicHours) {
        this.mechanicHours = mechanicHours;
    }

    @Override
    public String toString() {
        return "Depot{" +
                "id=" + id +
                ", mechanicHours=" + mechanicHours +
                '}';
    }
}
