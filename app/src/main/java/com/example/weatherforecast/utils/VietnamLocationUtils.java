package com.example.weatherforecast.utils;

public class VietnamLocationUtils {
    private static final Province[] PROVINCES = {
            new Province("Hà Nội", 21.0285, 105.8542),
            new Province("Hải Phòng", 20.8449, 106.6881),
            new Province("Tuyên Quang", 21.8183, 105.2145),
            new Province("Lào Cai", 22.4822, 103.9721),
            new Province("Lai Châu", 22.3966, 103.4558),
            new Province("Điện Biên", 21.3929, 103.0230),
            new Province("Sơn La", 21.3257, 103.9179),
            new Province("Thái Nguyên", 21.5944, 105.8481),
            new Province("Lạng Sơn", 21.8478, 106.7598),
            new Province("Quảng Ninh", 20.9519, 107.0872),
            new Province("Phú Thọ", 21.3622, 105.3909),
            new Province("Bắc Ninh", 21.1864, 106.0763),
            new Province("Hưng Yên", 20.6464, 106.0519),
            new Province("Ninh Bình", 20.2506, 105.9744),
            new Province("Thanh Hóa", 19.8076, 105.7844),
            new Province("Nghệ An", 18.6792, 105.6819),
            new Province("Hà Tĩnh", 18.3428, 105.9056),
            new Province("Quảng Trị", 16.7473, 107.1924),
            new Province("Thừa Thiên Huế", 16.4637, 107.5909),
            new Province("Đà Nẵng", 16.0544, 108.2022),
            new Province("Quảng Ngãi", 15.1205, 108.7922),
            new Province("Gia Lai", 13.9807, 108.0000),
            new Province("Khánh Hòa", 12.2388, 109.1967),
            new Province("Lâm Đồng", 11.9404, 108.4583),
            new Province("Đắk Lắk", 12.6667, 108.0500),
            new Province("Đồng Nai", 11.0264, 106.8372),
            new Province("Tây Ninh", 11.3140, 106.1093),
            new Province("Thành phố Hồ Chí Minh", 10.8231, 106.6297),
            new Province("Vĩnh Long", 10.2537, 105.9722),
            new Province("Đồng Tháp", 10.4577, 105.6439),
            new Province("An Giang", 10.5216, 105.1259),
            new Province("Cà Mau", 9.1767, 105.1524),
            new Province("Cần Thơ", 10.0452, 105.7469),
            new Province("Cao Bằng", 22.6756, 106.2500)
    };

    /**
     * Tìm tên tỉnh thành gần nhất từ tọa độ GPS
     * @param latitude Vĩ độ
     * @param longitude Kinh độ
     * @return Tên tỉnh thành gần nhất
     */
    public static String getProvinceName(double latitude, double longitude) {
        double minDistance = Double.MAX_VALUE;
        String nearestProvince = "Việt Nam";

        for (Province province : PROVINCES) {
            double distance = calculateDistance(latitude, longitude, province.latitude, province.longitude);
            if (distance < minDistance) {
                minDistance = distance;
                nearestProvince = province.name;
            }
        }

        return nearestProvince;
    }

    /**
     * Tính khoảng cách giữa 2 điểm GPS (Haversine formula)
     * @param lat1 Vĩ độ điểm 1
     * @param lon1 Kinh độ điểm 1
     * @param lat2 Vĩ độ điểm 2
     * @param lon2 Kinh độ điểm 2
     * @return Khoảng cách tính bằng km
     */
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Bán kính Trái Đất (km)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c;

        return distance;
    }

    /**
     * Class để lưu thông tin tỉnh thành
     */
    private static class Province {
        String name;
        double latitude;
        double longitude;

        Province(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
