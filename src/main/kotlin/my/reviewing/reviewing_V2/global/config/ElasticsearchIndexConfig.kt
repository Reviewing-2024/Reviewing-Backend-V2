package my.reviewing.reviewing_V2.global.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration

@Configuration
class ElasticsearchIndexConfig(
    private val elasticsearchClient: ElasticsearchClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun createIndex() {
        val indexName = "courses"

        val exists = elasticsearchClient.indices().exists { it.index(indexName) }.value()
        if (exists) {
            log.info("Elasticsearch 인덱스 '{}' 이미 존재함, 스킵", indexName)
            return
        }

        elasticsearchClient.indices().create { create ->
            create.index(indexName)
                .mappings { m ->
                    m.properties("id") { p -> p.keyword { it } }
                        .properties("platform") { p -> p.keyword { it } }
                        .properties("title") { p ->
                            p.text { t ->
                                t.fields("korean") { f -> f.text { it.analyzer("nori") } }
                                    .fields("english") { f -> f.text { it.analyzer("standard") } }
                            }
                        }
                        .properties("teacher") { p ->
                            p.text { t ->
                                t.fields("korean") { f -> f.text { it.analyzer("nori") } }
                                    .fields("english") { f -> f.text { it.analyzer("standard") } }
                            }
                        }
                        .properties("embedding") { p ->
                            p.denseVector { d ->
                                d.dims(512).index(true).similarity("dot_product")
                            }
                        }
                }
        }

        log.info("Elasticsearch 인덱스 '{}' 생성 완료", indexName)
    }
}
