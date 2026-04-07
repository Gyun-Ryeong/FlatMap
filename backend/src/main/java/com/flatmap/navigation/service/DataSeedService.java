package com.flatmap.navigation.service;

import com.flatmap.navigation.entity.*;
import com.flatmap.navigation.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 외부 API가 아직 연동되지 않았거나 데이터가 없을 때 사용할
 * 성남시 실제 위치 기반 시드 데이터를 생성한다.
 * name 기준으로 중복 체크하여 이미 존재하면 건너뛴다.
 */
@Service
public class DataSeedService {

    private static final Logger log = LoggerFactory.getLogger(DataSeedService.class);

    private final SecurityLightRepository securityLightRepo;
    private final AccidentZoneRepository accidentZoneRepo;
    private final CctvLocationRepository cctvRepo;
    private final ProtectedZoneRepository protectedZoneRepo;
    private final WelfareFacilityRepository welfareFacilityRepo;
    private final ShadeShelterRepository shadeShelterRepo;
    private final SeniorCenterRepository seniorCenterRepo;

    public DataSeedService(SecurityLightRepository securityLightRepo,
                           AccidentZoneRepository accidentZoneRepo,
                           CctvLocationRepository cctvRepo,
                           ProtectedZoneRepository protectedZoneRepo,
                           WelfareFacilityRepository welfareFacilityRepo,
                           ShadeShelterRepository shadeShelterRepo,
                           SeniorCenterRepository seniorCenterRepo) {
        this.securityLightRepo = securityLightRepo;
        this.accidentZoneRepo = accidentZoneRepo;
        this.cctvRepo = cctvRepo;
        this.protectedZoneRepo = protectedZoneRepo;
        this.welfareFacilityRepo = welfareFacilityRepo;
        this.shadeShelterRepo = shadeShelterRepo;
        this.seniorCenterRepo = seniorCenterRepo;
    }

    @Transactional
    public Map<String, Object> seedAll() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("securityLights", seedSecurityLights());
        result.put("accidentZones", seedAccidentZones());
        result.put("cctv", seedCctv());
        result.put("protectedZones", seedProtectedZones());
        result.put("welfareFacilities", seedWelfareFacilities());
        result.put("shadeShelters", seedShadeShelters());
        result.put("seniorCenters", seedSeniorCenters());
        log.info("전체 시드 데이터 삽입 완료: {}", result);
        return result;
    }

    // ========== 보안등 15건 ==========
    private int seedSecurityLights() {
        List<SecurityLight> seeds = List.of(
            // 태평역 주변
            new SecurityLight("[SEED] 태평역 1번출구 앞", 37.4472, 127.1468, "성남시 수정구 태평동 산성대로", "41131"),
            new SecurityLight("[SEED] 태평역 성남대로 교차", 37.4465, 127.1450, "성남시 수정구 태평동 성남대로", "41131"),
            new SecurityLight("[SEED] 태평동 산성대로변", 37.4480, 127.1490, "성남시 수정구 태평동 산성대로 331", "41131"),
            // 모란역 주변
            new SecurityLight("[SEED] 모란역 2번출구 앞", 37.4328, 127.1293, "성남시 중원구 성남동 모란로", "41133"),
            new SecurityLight("[SEED] 모란시장 입구", 37.4335, 127.1280, "성남시 중원구 성남동 성남대로", "41133"),
            new SecurityLight("[SEED] 모란역 성남대로변", 37.4320, 127.1305, "성남시 중원구 성남동 성남대로 1165", "41133"),
            // 야탑역 주변
            new SecurityLight("[SEED] 야탑역 3번출구 앞", 37.4113, 127.1275, "성남시 분당구 야탑동", "41135"),
            new SecurityLight("[SEED] 야탑로 도보구간", 37.4100, 127.1260, "성남시 분당구 야탑동 야탑로", "41135"),
            new SecurityLight("[SEED] 야탑동 분당내곡로변", 37.4090, 127.1290, "성남시 분당구 야탑동 분당내곡로", "41135"),
            // 서현역 주변
            new SecurityLight("[SEED] 서현역 1번출구 앞", 37.3847, 127.1248, "성남시 분당구 서현동", "41135"),
            new SecurityLight("[SEED] 서현로 도보구간", 37.3855, 127.1265, "성남시 분당구 서현동 서현로", "41135"),
            new SecurityLight("[SEED] 서현동 돌마로변", 37.3838, 127.1230, "성남시 분당구 서현동 돌마로", "41135"),
            // 수내역 주변
            new SecurityLight("[SEED] 수내역 1번출구 앞", 37.3780, 127.1155, "성남시 분당구 수내동", "41135"),
            new SecurityLight("[SEED] 수내동 분당로변", 37.3770, 127.1140, "성남시 분당구 수내동 분당로", "41135"),
            new SecurityLight("[SEED] 수내역 황새울로변", 37.3790, 127.1170, "성남시 분당구 수내동 황새울로", "41135")
        );
        int count = 0;
        for (SecurityLight s : seeds) {
            if (!securityLightRepo.existsByName(s.getName())) {
                securityLightRepo.save(s);
                count++;
            }
        }
        log.info("보안등 시드: {}건 삽입 ({}건 중복 건너뜀)", count, seeds.size() - count);
        return count;
    }

    // ========== 사고다발지 10건 ==========
    private int seedAccidentZones() {
        List<AccidentZone> seeds = List.of(
            new AccidentZone("[SEED] 모란사거리 사고다발", 37.4330, 127.1295, "NORMAL", "41133"),
            new AccidentZone("[SEED] 태평사거리 사고다발", 37.4468, 127.1455, "NORMAL", "41131"),
            new AccidentZone("[SEED] 성남대로-산성대로 교차", 37.4450, 127.1430, "ICY", "41131"),
            new AccidentZone("[SEED] 야탑역 사거리 사고다발", 37.4108, 127.1270, "NORMAL", "41135"),
            new AccidentZone("[SEED] 분당 정자동 사거리", 37.3660, 127.1090, "NORMAL", "41135"),
            new AccidentZone("[SEED] 수정로 결빙 위험구간", 37.4530, 127.1550, "ICY", "41131"),
            new AccidentZone("[SEED] 남한산성로 결빙구간", 37.4570, 127.1700, "ICY", "41131"),
            new AccidentZone("[SEED] 서현역 사거리 사고다발", 37.3845, 127.1250, "NORMAL", "41135"),
            new AccidentZone("[SEED] 단대오거리 사고다발", 37.4445, 127.1575, "NORMAL", "41131"),
            new AccidentZone("[SEED] 돌마로 결빙 위험구간", 37.3820, 127.1190, "ICY", "41135")
        );
        int count = 0;
        for (AccidentZone a : seeds) {
            if (!accidentZoneRepo.existsByName(a.getName())) {
                accidentZoneRepo.save(a);
                count++;
            }
        }
        log.info("사고다발지 시드: {}건 삽입 ({}건 중복 건너뜀)", count, seeds.size() - count);
        return count;
    }

    // ========== CCTV 12건 ==========
    private int seedCctv() {
        List<CctvLocation> seeds = List.of(
            new CctvLocation("[SEED] 모란역 교차로 CCTV", 37.4332, 127.1290, "성남시 중원구 성남동"),
            new CctvLocation("[SEED] 모란시장 앞 CCTV", 37.4338, 127.1278, "성남시 중원구 성남동"),
            new CctvLocation("[SEED] 태평역 교차로 CCTV", 37.4470, 127.1465, "성남시 수정구 태평동"),
            new CctvLocation("[SEED] 성남시청 앞 CCTV", 37.4200, 127.1268, "성남시 수정구 수정로 283"),
            new CctvLocation("[SEED] 야탑역 교차로 CCTV", 37.4110, 127.1272, "성남시 분당구 야탑동"),
            new CctvLocation("[SEED] 야탑초등학교 통학로 CCTV", 37.4095, 127.1255, "성남시 분당구 야탑동"),
            new CctvLocation("[SEED] 서현역 교차로 CCTV", 37.3848, 127.1252, "성남시 분당구 서현동"),
            new CctvLocation("[SEED] 서현초등학교 통학로 CCTV", 37.3860, 127.1270, "성남시 분당구 서현동"),
            new CctvLocation("[SEED] 수내역 교차로 CCTV", 37.3782, 127.1158, "성남시 분당구 수내동"),
            new CctvLocation("[SEED] 단대오거리 CCTV", 37.4442, 127.1572, "성남시 수정구 단대동"),
            new CctvLocation("[SEED] 산성역 입구 CCTV", 37.4565, 127.1268, "성남시 수정구 산성동"),
            new CctvLocation("[SEED] 신흥역 교차로 CCTV", 37.4402, 127.1508, "성남시 수정구 신흥동")
        );
        int count = 0;
        for (CctvLocation c : seeds) {
            if (!cctvRepo.existsByName(c.getName())) {
                cctvRepo.save(c);
                count++;
            }
        }
        log.info("CCTV 시드: {}건 삽입 ({}건 중복 건너뜀)", count, seeds.size() - count);
        return count;
    }

    // ========== 교통약자 보호구역 8건 ==========
    private int seedProtectedZones() {
        List<ProtectedZone> seeds = List.of(
            // 어린이보호구역
            new ProtectedZone("[SEED] 성남초등학교 앞", 37.4370, 127.1360, "CHILD", "성남시 중원구 성남동", "41133"),
            new ProtectedZone("[SEED] 단대초등학교 앞", 37.4490, 127.1600, "CHILD", "성남시 수정구 단대동", "41131"),
            new ProtectedZone("[SEED] 야탑초등학교 앞", 37.4092, 127.1250, "CHILD", "성남시 분당구 야탑동", "41135"),
            new ProtectedZone("[SEED] 서현초등학교 앞", 37.3862, 127.1275, "CHILD", "성남시 분당구 서현동", "41135"),
            // 노인보호구역
            new ProtectedZone("[SEED] 모란전통시장 앞", 37.4340, 127.1282, "SENIOR", "성남시 중원구 성남동", "41133"),
            new ProtectedZone("[SEED] 수정노인복지관 앞", 37.4522, 127.1538, "SENIOR", "성남시 수정구 수진동", "41131"),
            // 장애인보호구역
            new ProtectedZone("[SEED] 성남시장애인복지관 앞", 37.4305, 127.1548, "DISABLED", "성남시 중원구 여수동", "41133"),
            new ProtectedZone("[SEED] 분당장애인복지관 앞", 37.3870, 127.1158, "DISABLED", "성남시 분당구 정자동", "41135")
        );
        int count = 0;
        for (ProtectedZone p : seeds) {
            if (!protectedZoneRepo.existsByName(p.getName())) {
                protectedZoneRepo.save(p);
                count++;
            }
        }
        log.info("교통약자 보호구역 시드: {}건 삽입 ({}건 중복 건너뜀)", count, seeds.size() - count);
        return count;
    }

    // ========== 장애인복지시설 6건 ==========
    private int seedWelfareFacilities() {
        List<WelfareFacility> seeds = List.of(
            new WelfareFacility("[SEED] 성남시장애인복지관", 37.4308, 127.1545, "종합복지관", "성남시 중원구 여수동 둔촌대로 101", "031-729-1400", "41133"),
            new WelfareFacility("[SEED] 성남시장애인종합복지관(분당)", 37.3865, 127.1155, "종합복지관", "성남시 분당구 정자동 불정로 90", "031-712-7200", "41135"),
            new WelfareFacility("[SEED] 분당장애인주간보호센터", 37.3830, 127.1200, "주간보호", "성남시 분당구 서현동", "031-718-3300", "41135"),
            new WelfareFacility("[SEED] 중원구장애인주간보호센터", 37.4280, 127.1370, "주간보호", "성남시 중원구 중앙동", "031-731-8500", "41133"),
            new WelfareFacility("[SEED] 수정구장애인주간보호센터", 37.4500, 127.1520, "주간보호", "성남시 수정구 수진동", "031-732-4400", "41131"),
            new WelfareFacility("[SEED] 성남시직업재활시설", 37.4260, 127.1410, "직업재활", "성남시 중원구 금광동", "031-735-3311", "41133")
        );
        int count = 0;
        for (WelfareFacility w : seeds) {
            if (!welfareFacilityRepo.existsByName(w.getName())) {
                welfareFacilityRepo.save(w);
                count++;
            }
        }
        log.info("장애인복지시설 시드: {}건 삽입 ({}건 중복 건너뜀)", count, seeds.size() - count);
        return count;
    }

    // ========== 그늘막 10건 ==========
    private int seedShadeShelters() {
        List<ShadeShelter> seeds = List.of(
            // 탄천 산책로
            new ShadeShelter("[SEED] 탄천 산책로 그늘막(야탑)", 37.4100, 127.1290, "성남시 분당구 야탑동 탄천변", "41135"),
            new ShadeShelter("[SEED] 탄천 산책로 그늘막(성남동)", 37.4200, 127.1310, "성남시 중원구 성남동 탄천변", "41133"),
            new ShadeShelter("[SEED] 탄천 산책로 그늘막(중앙동)", 37.4300, 127.1330, "성남시 중원구 중앙동 탄천변", "41133"),
            // 율동공원
            new ShadeShelter("[SEED] 율동공원 그늘막1", 37.3775, 127.0955, "성남시 분당구 율동 공원길", "41135"),
            new ShadeShelter("[SEED] 율동공원 그늘막2", 37.3768, 127.0970, "성남시 분당구 율동 산책로", "41135"),
            // 공원
            new ShadeShelter("[SEED] 중앙공원 그늘막", 37.4350, 127.1370, "성남시 중원구 중앙동", "41133"),
            new ShadeShelter("[SEED] 분당중앙공원 그늘막", 37.3840, 127.1240, "성남시 분당구 서현동", "41135"),
            // 버스정류장 주변
            new ShadeShelter("[SEED] 모란역 정류장 그늘막", 37.4325, 127.1298, "성남시 중원구 성남동", "41133"),
            new ShadeShelter("[SEED] 야탑역 정류장 그늘막", 37.4108, 127.1268, "성남시 분당구 야탑동", "41135"),
            new ShadeShelter("[SEED] 서현역 정류장 그늘막", 37.3850, 127.1242, "성남시 분당구 서현동", "41135")
        );
        int count = 0;
        for (ShadeShelter s : seeds) {
            if (!shadeShelterRepo.existsByName(s.getName())) {
                shadeShelterRepo.save(s);
                count++;
            }
        }
        log.info("그늘막 시드: {}건 삽입 ({}건 중복 건너뜀)", count, seeds.size() - count);
        return count;
    }

    // ========== 노인복지관 5건 ==========
    private int seedSeniorCenters() {
        List<SeniorCenter> seeds = List.of(
            new SeniorCenter("[SEED] 성남시수정노인종합복지관", 37.4525, 127.1535, "성남시 수정구 수진동 수정로 398", "031-732-1230", "41131"),
            new SeniorCenter("[SEED] 성남시중원노인종합복지관", 37.4300, 127.1390, "성남시 중원구 중앙동 중앙로 53", "031-748-0091", "41133"),
            new SeniorCenter("[SEED] 성남시분당노인종합복지관", 37.3872, 127.1212, "성남시 분당구 정자동 분당로 55", "031-783-8870", "41135"),
            new SeniorCenter("[SEED] 성남시은행종합사회복지관", 37.4465, 127.1940, "성남시 중원구 은행동 둔촌대로 200", "031-726-0505", "41133"),
            new SeniorCenter("[SEED] 성남시고령자복지센터", 37.4250, 127.1340, "성남시 중원구 금광동 성남대로 1045", "031-729-2500", "41133")
        );
        int count = 0;
        for (SeniorCenter s : seeds) {
            if (!seniorCenterRepo.existsByName(s.getName())) {
                seniorCenterRepo.save(s);
                count++;
            }
        }
        log.info("노인복지관 시드: {}건 삽입 ({}건 중복 건너뜀)", count, seeds.size() - count);
        return count;
    }
}
