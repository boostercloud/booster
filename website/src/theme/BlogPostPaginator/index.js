import React from 'react'
import BlogPostPaginator from '@theme-original/BlogPostPaginator'
import Utterance from '@site/src/components/Utterance'

export default function BlogPostPaginatorWrapper(props) {
  return (
    <>
      <BlogPostPaginator {...props} />
      <Utterance />
    </>
  )
}
