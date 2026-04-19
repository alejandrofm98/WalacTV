package com.example.walactv.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY numero ASC LIMIT :limit OFFSET :offset")
    suspend fun getAllPaged(limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE country = :country ORDER BY numero ASC LIMIT :limit OFFSET :offset")
    suspend fun getByCountryPaged(country: String, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE grupoNormalizado = :group ORDER BY numero ASC LIMIT :limit OFFSET :offset")
    suspend fun getByGroupPaged(group: String, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE country = :country AND grupoNormalizado = :group ORDER BY numero ASC LIMIT :limit OFFSET :offset")
    suspend fun getByCountryAndGroupPaged(country: String, group: String, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE nombreNormalizado LIKE '%' || :query || '%' ORDER BY numero ASC LIMIT 100")
    suspend fun search(query: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE nombreNormalizado LIKE '%' || :query || '%' AND country = :country ORDER BY numero ASC LIMIT 100")
    suspend fun searchByCountry(query: String, country: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE nombreNormalizado LIKE '%' || :query || '%' AND grupoNormalizado = :group ORDER BY numero ASC LIMIT 100")
    suspend fun searchByGroup(query: String, group: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE nombreNormalizado LIKE '%' || :query || '%' AND country = :country AND grupoNormalizado = :group ORDER BY numero ASC LIMIT 100")
    suspend fun searchByCountryAndGroup(query: String, country: String, group: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM channels WHERE country = :country AND grupoNormalizado = :group")
    suspend fun getCountByCountryAndGroup(country: String, group: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE country = :country")
    suspend fun getCountByCountry(country: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE grupoNormalizado = :group")
    suspend fun getCountByGroup(group: String): Int

    @Query("SELECT DISTINCT country FROM channels WHERE country != '' ORDER BY country")
    suspend fun getDistinctCountries(): List<String>

    @Query("SELECT DISTINCT grupoNormalizado FROM channels WHERE grupoNormalizado != '' ORDER BY grupoNormalizado")
    suspend fun getDistinctGroups(): List<String>

    @Query("SELECT DISTINCT grupoNormalizado FROM channels WHERE country = :country AND grupoNormalizado != '' ORDER BY grupoNormalizado")
    suspend fun getDistinctGroupsByCountry(country: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY nombreNormalizado ASC LIMIT :limit OFFSET :offset")
    suspend fun getAllPaged(limit: Int, offset: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE country = :country ORDER BY nombreNormalizado ASC LIMIT :limit OFFSET :offset")
    suspend fun getByCountryPaged(country: String, limit: Int, offset: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE grupoNormalizado = :group ORDER BY nombreNormalizado ASC LIMIT :limit OFFSET :offset")
    suspend fun getByGroupPaged(group: String, limit: Int, offset: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE country = :country AND grupoNormalizado = :group ORDER BY nombreNormalizado ASC LIMIT :limit OFFSET :offset")
    suspend fun getByCountryAndGroupPaged(country: String, group: String, limit: Int, offset: Int): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE nombreNormalizado LIKE '%' || :query || '%' ORDER BY nombreNormalizado ASC LIMIT 100")
    suspend fun search(query: String): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE nombreNormalizado LIKE '%' || :query || '%' AND country = :country ORDER BY nombreNormalizado ASC LIMIT 100")
    suspend fun searchByCountry(query: String, country: String): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE nombreNormalizado LIKE '%' || :query || '%' AND grupoNormalizado = :group ORDER BY nombreNormalizado ASC LIMIT 100")
    suspend fun searchByGroup(query: String, group: String): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE nombreNormalizado LIKE '%' || :query || '%' AND country = :country AND grupoNormalizado = :group ORDER BY nombreNormalizado ASC LIMIT 100")
    suspend fun searchByCountryAndGroup(query: String, country: String, group: String): List<MovieEntity>

    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM movies WHERE country = :country AND grupoNormalizado = :group")
    suspend fun getCountByCountryAndGroup(country: String, group: String): Int

    @Query("SELECT COUNT(*) FROM movies WHERE country = :country")
    suspend fun getCountByCountry(country: String): Int

    @Query("SELECT COUNT(*) FROM movies WHERE grupoNormalizado = :group")
    suspend fun getCountByGroup(group: String): Int

    @Query("SELECT DISTINCT country FROM movies WHERE country != '' ORDER BY country")
    suspend fun getDistinctCountries(): List<String>

    @Query("SELECT DISTINCT grupoNormalizado FROM movies WHERE grupoNormalizado != '' ORDER BY grupoNormalizado")
    suspend fun getDistinctGroups(): List<String>

    @Query("SELECT DISTINCT grupoNormalizado FROM movies WHERE country = :country AND grupoNormalizado != '' ORDER BY grupoNormalizado")
    suspend fun getDistinctGroupsByCountry(country: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Query("DELETE FROM movies")
    suspend fun deleteAll()
}

@Dao
interface SeriesDao {
    @Query("SELECT MIN(id) as id, MIN(providerId) as providerId, MIN(logo) as logo, MIN(country) as country, 0 as temporada, 0 as episodio, serieName, serieName as nombreNormalizado, MIN(grupoNormalizado) as grupoNormalizado FROM series GROUP BY serieName ORDER BY serieName ASC LIMIT :limit OFFSET :offset")
    suspend fun getAllPaged(limit: Int, offset: Int): List<SeriesEntity>

    @Query("SELECT MIN(id) as id, MIN(providerId) as providerId, MIN(logo) as logo, MIN(country) as country, 0 as temporada, 0 as episodio, serieName, serieName as nombreNormalizado, MIN(grupoNormalizado) as grupoNormalizado FROM series WHERE country = :country GROUP BY serieName ORDER BY serieName ASC LIMIT :limit OFFSET :offset")
    suspend fun getByCountryPaged(country: String, limit: Int, offset: Int): List<SeriesEntity>

    @Query("SELECT MIN(id) as id, MIN(providerId) as providerId, MIN(logo) as logo, MIN(country) as country, 0 as temporada, 0 as episodio, serieName, serieName as nombreNormalizado, MIN(grupoNormalizado) as grupoNormalizado FROM series WHERE grupoNormalizado = :group GROUP BY serieName ORDER BY serieName ASC LIMIT :limit OFFSET :offset")
    suspend fun getByGroupPaged(group: String, limit: Int, offset: Int): List<SeriesEntity>

    @Query("SELECT MIN(id) as id, MIN(providerId) as providerId, MIN(logo) as logo, MIN(country) as country, 0 as temporada, 0 as episodio, serieName, serieName as nombreNormalizado, MIN(grupoNormalizado) as grupoNormalizado FROM series WHERE country = :country AND grupoNormalizado = :group GROUP BY serieName ORDER BY serieName ASC LIMIT :limit OFFSET :offset")
    suspend fun getByCountryAndGroupPaged(country: String, group: String, limit: Int, offset: Int): List<SeriesEntity>

    @Query("SELECT id, providerId, logo, country, temporada, episodio, serieName, nombreNormalizado, grupoNormalizado FROM series WHERE serieName LIKE '%' || :query || '%' LIMIT 500")
    suspend fun search(query: String): List<SeriesEntity>

    @Query("SELECT id, providerId, logo, country, temporada, episodio, serieName, nombreNormalizado, grupoNormalizado FROM series WHERE serieName LIKE '%' || :query || '%' AND country = :country LIMIT 500")
    suspend fun searchByCountry(query: String, country: String): List<SeriesEntity>

    @Query("SELECT id, providerId, logo, country, temporada, episodio, serieName, nombreNormalizado, grupoNormalizado FROM series WHERE serieName LIKE '%' || :query || '%' AND grupoNormalizado = :group LIMIT 500")
    suspend fun searchByGroup(query: String, group: String): List<SeriesEntity>

    @Query("SELECT id, providerId, logo, country, temporada, episodio, serieName, nombreNormalizado, grupoNormalizado FROM series WHERE serieName LIKE '%' || :query || '%' AND country = :country AND grupoNormalizado = :group LIMIT 500")
    suspend fun searchByCountryAndGroup(query: String, country: String, group: String): List<SeriesEntity>

    @Query("SELECT COUNT(DISTINCT serieName) FROM series")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(DISTINCT serieName) FROM series WHERE country = :country AND grupoNormalizado = :group")
    suspend fun getCountByCountryAndGroup(country: String, group: String): Int

    @Query("SELECT COUNT(DISTINCT serieName) FROM series WHERE country = :country")
    suspend fun getCountByCountry(country: String): Int

    @Query("SELECT COUNT(DISTINCT serieName) FROM series WHERE grupoNormalizado = :group")
    suspend fun getCountByGroup(group: String): Int

    @Query("SELECT DISTINCT country FROM series WHERE country != '' ORDER BY country")
    suspend fun getDistinctCountries(): List<String>

    @Query("SELECT DISTINCT grupoNormalizado FROM series WHERE grupoNormalizado != '' ORDER BY grupoNormalizado")
    suspend fun getDistinctGroups(): List<String>

    @Query("SELECT DISTINCT grupoNormalizado FROM series WHERE country = :country AND grupoNormalizado != '' ORDER BY grupoNormalizado")
    suspend fun getDistinctGroupsByCountry(country: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(series: List<SeriesEntity>)

    @Query("DELETE FROM series")
    suspend fun deleteAll()
}
