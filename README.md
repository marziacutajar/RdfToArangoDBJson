RDF to ArangoDB JSON Transformer

This project provides a way of parsing RDF data into a JSON format suitable for the multimodel database ArangoDB.

How is the RDF parsed?

- Each unique namespace, subject, predicate, and object is transformed into a JSON document.
- Each JSON document has a type attribute that specifies whether it is a URI resource, a blank node, a literal, a namespace, or a property. 
- The relationship between a subject and an object is represented using a graph edge between them, and the uri of the predicate relating them is stored as an attribute of the edge (helps when filtering on predicate)
