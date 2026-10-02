SELECT "id_zakázky", "id_artiklu", "množství", "disponibilní"
FROM (SELECT "t2"."id_řádku_zakázky", "t2"."id_zakázky", "t2"."id_artiklu", "t2"."množství", "t4"."kód_skladu", "t4"."disponibilní_pro_ověření", COALESCE("t4"."disponibilní_pro_ověření", 0) AS "disponibilní"
        FROM (SELECT "t"."id_řádku_zakázky", "t"."id_zakázky", "t"."id_artiklu", "t"."množství"
                FROM (SELECT "IDSDOK" AS "id_řádku_zakázky", "IDHDOK" AS "id_zakázky", "IDZBOZI" AS "id_artiklu", "MNCELKEMPOCET" AS "množství"
                        FROM "QSDOK_ZAK") AS "t"
                    INNER JOIN (SELECT *
                        FROM (SELECT "IDHDOK" AS "id_zakázky", "CIS_DOK" AS "číslo_zakázky", "IDSUBJEKT" AS "id_subjektu", "VLSTAVHDOK" AS "stav_zakázky"
                                FROM (
    SELECT * FROM QHDOK_ZAK WHERE TYP_DOK='POB'
) AS "zakázka__filter") AS "t0"
                        WHERE "id_zakázky" = :zakázka_id AND "stav_zakázky" = 1) AS "t1" ON "t"."id_zakázky" = "t1"."id_zakázky") AS "t2"
            LEFT JOIN (SELECT *
                FROM (SELECT "IDZBOZI" AS "id_artiklu", "CIS_SKLAD" AS "kód_skladu", "DISPONIBILNI" AS "disponibilní_pro_ověření"
                        FROM "QZBOZSKL") AS "t3"
                WHERE "kód_skladu" = :sklad) AS "t4" ON "t2"."id_artiklu" = "t4"."id_artiklu") AS "t5"
WHERE "t5"."disponibilní" < "t5"."množství"
