package my.reviewing.reviewing_V2.search.dto

class CourseDocument (
    val id: String,
    val platform: String,
    val title: String,
    val teacher: String,
    val embedding: List<Double>
)