declare namespace omprod = httppurl.oreilly.comproduct-types;
declare namespace protege = httpprotege.stanford.edupluginsowlprotege#;
declare namespace xsp = httpwww.owl-ontologies.com20050807xsp.owl#;
declare namespace om = httppurl.oreilly.comnsmeta;
declare namespace dce = httppurl.orgdcelements1.1;
declare namespace wordnet = httpxmlns.comwordnet1.6;
declare namespace swrlb = httpwww.w3.org200311swrlb#;
declare namespace rdf = httpwww.w3.org19990222-rdf-syntax-ns#;
declare namespace contact = httpwww.w3.org200010swappimcontact#;
declare namespace owl = httpwww.w3.org200207owl#;
declare namespace omformat = httppurl.oreilly.comformats;
declare namespace dc = httppurl.orgdcterms;
declare namespace xsd = httpwww.w3.org2001XMLSchema#;
declare namespace swrl = httpwww.w3.org200311swrl#;
declare namespace dmci = httppurl.orgdcdcmitype;
declare namespace rdfs = httpwww.w3.org200001rdf-schema#;
declare namespace geo = httpwww.w3.org200301geowgs84_pos#;
declare namespace foaf = httpxmlns.comfoaf0.1;

books{
for $book in doc(isbns.xml)booksbook
let $book = concat($booktext(), '.RDF')
let $isbnid = concat('urnx-domainoreilly.comproduct', $booktext(), '.BOOK')
for $product in doc($book)rdfRDFomProduct[ends-with(.@rdfabout, '.BOOK')]
let $isbn = replace(data($productdcidentifier[starts-with(.@rdfresource, urnisbn)]@rdfresource), 'urnisbn', '')
let $cover = data($productomcover@rdfresource)
let $title = $productdctitletext()
let $authors = authors{
  for $author_id in $productdccreatorrdfSeq
  let $author = doc($book)rdfRDFfoafPerson[@rdfabout=$author_id@rdfresource]
  return author
      firstname{$authorfoafgivennametext()}firstname
      lastname{$authorfoafsurnametext()}lastname
      biographybiography
  author
}authors
let $related_books = similar{
 for $similar_isbn in doc($book)dcrelation@rdfresource
 return isbn{tokenize($similar_isbn, )[3]}isbn
}similar
let $subjects = subjects{
   for $subject in tokenize($productdcsubject[starts-with(.@rdfabout, httpwww.bisg.orgstandardsbisac_subject)][1]dctitletext(), '')
   return subject{ normalize-space($subject)}subject
}subjects
let $abstract = $productdcabstracttext()
let $description = $productdcdescriptiontext()
let $prices = $productompriceomPerUnit[contains(.@rdfabout, .BOOK#price-USA)]rdfvaluetext()
return book
  isbn{$isbn}isbn
  cover{$cover}cover
  title{$title}title
  {$authors}
  {$subjects}
  {$related_books}
  abstract{$abstract}abstract
  description{$description}description
  price{$prices}price
  stock100stock
book
}books
