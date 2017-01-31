package com.jinyufeili.minas.wxbot.data;

/**
 * Created by pw on 09/10/2016.
 */
public class Resident {

    private int userId;

    private String name;

    private int region;

    private int building;

    private int unit;

    private int houseNumber;

    private String avatarId;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRegion() {
        return region;
    }

    public void setRegion(int region) {
        this.region = region;
    }

    public int getBuilding() {
        return building;
    }

    public void setBuilding(int building) {
        this.building = building;
    }

    public int getUnit() {
        return unit;
    }

    public void setUnit(int unit) {
        this.unit = unit;
    }

    public int getHouseNumber() {
        return houseNumber;
    }

    public void setHouseNumber(int houseNumber) {
        this.houseNumber = houseNumber;
    }

    @Override
    public String toString() {
        return "Resident{" +
                "userId=" + userId +
                ", name='" + name + '\'' +
                ", region=" + region +
                ", building=" + building +
                ", unit=" + unit +
                ", houseNumber=" + houseNumber +
                ", avatarId='" + avatarId + '\'' +
                '}';
    }

    public void setAvatarId(String avatarId) {
        this.avatarId = avatarId;
    }

    public String getAvatarId() {
        return avatarId;
    }
}
