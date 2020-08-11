# Documentation principles and practices

The ultimate goal of a technical document is to translate the knowledge from the technology creators into the reader's mind so that they learn. The challenging 
part here is the one in which they learn. It is challenging because, under the same amount of information, a person can suffer an information overload because 
we (humans) don't have the same information-processing capacity. That idea is going to work as our compass, it should drive our efforts so people with less 
capacity is still able to follow and understand our documentation.

To achieve our goal I propose writing documentation following these principles:

1. Clean and Clear
2. Simple
3. Coherent
4. Explicit
5. Attractive
6. Inclusive
7. Cohesive

## Principles

### Clean and Clear

Less is more. Apple is, among many others, a good example of creating clean and clear content, where visual elements are carefully chosen to look beautiful
(e.g. [Apple's swift UI](https://developer.apple.com/tutorials/swiftui)) and making the reader getting the point as soon as possible.

The intention of every section, paragraph, and sentence must be clear, we should avoid writing details of 2 different things even when they are related. 
It is better to link pages and keep the focus and the intention clear, Wikipedia is the best example on this.

### Simple

Technical writings deal with different backgrounds and expertise from the readers. We should not assume the reader knows everything we are talking about 
but we should not explain everything in the same paragraph or section. Every section has a goal to stick to the goal and link to internal or external resources 
to go deeper.

Diagrams are great tools, you know a picture is worth more than a thousand words unless that picture contains too much information. 
Keep it simple intentionally omitting details.

### Coherent

The documentation tells a story. Every section should integrate naturally without making the reader switch between different contexts. Text, diagrams, 
and code examples should support each other without introducing abrupt changes breaking the reader’s flow. Also, the font, colors, diagrams, code samples,
animations, and all the visual elements we include, should support the story we are telling.

### Explicit

Go straight to the point without assuming the readers should know about something. Again, link internal or external resources to clarify.

The index of the whole content must be visible all the time so the reader knows exactly where they are and what is left.

### Attractive

Our text must be nice to read, our diagrams delectable to see, and our site… a feast for the eyes!!

### Inclusive

Everybody should understand our writings, especially the topics at the top. We have arranged the documentation structure in a way that anybody can dig 
deeper by just going down so, sections 1 to 4 must be suitable for all ages. 

Use gender-neutral language to avoid the use of he, him, his to refer to undetermined gender. It is better to use their or they as a gender-neutral 
approach than s/he or similars.

### Cohesive

Writing short and concise sentences is good, but remember to use proper connectors (“Therefore”, “Besides”, “However”, “thus”, etc) that provide a 
sense of continuation to the whole paragraph. If not, when people read the paragraphs, their internal voice sounds like a robot with unnatural stops. 

For example, read this paragraph and try to hear your internal voice:

> Entities are created on the fly, by reducing the whole event stream. You shouldn't assume that they are stored anywhere.  Booster does create 
> automatic snapshots to make the reduction process efficient. You are the one in charge of writing the reducer function.

And now read this one:

> Entities are created on the fly by reducing the whole event stream. While you shouldn't assume that they are stored anywhere,  Booster does create automatic
> snapshots to make the reduction process efficient. In any case, this is opaque to you and the only thing you should care is to provide the reducer function.

Did you feel the difference? The latter makes you feel that everything is connected, it is more cohesive.

## Practices

There are many writing styles depending on the type of document. It is common within technical and scientific writing to use Inductive and/or Deductive styles 
for paragraphs. They have different outcomes and one style may suit better in one case or another, that is why it is important to know them, and decide which
one to use in every moment. Let’s see the difference with 2 recursive examples.

**Deductive paragraphs ease the reading for advanced users but still allows you to elaborate on ideas and concepts for newcomers**. In deductive paragraphs, 
the conclusions or definitions appear at the beginning, and then, details, facts, or supporting phrases complete the paragraph’s idea. By placing the 
conclusion in the first sentence, the reader immediately identifies the main point so they can decide to skip the whole paragraph or keep reading. 
If you take a look at the structure of this paragraph, it is deductive.

On the other hand, if you want to drive the readers' attention and play with it as if they were in a roller coaster, you can do so by using a different approach. 
In that approach, you first introduce the facts and ideas and then you wrap them with a conclusion. This style is more narrative and forces the reader to 
continue because the main idea is diluted in the whole paragraph. Once all the ideas are placed together, you can finally conclude the paragraph. **This style is 
called Inductive.**

The first paragraph is deductive and the last one is inductive. In general, it is better to use the deductive style, but if we stick to one, our writing will start looking weird and maybe boring. 
So decide one or another being conscious about your intention.
